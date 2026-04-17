"""FastAPI application entry point for the AI Worker service."""
import asyncio
import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import Counter, Histogram, generate_latest
from prometheus_fastapi_instrumentator import Instrumentator
from starlette.responses import JSONResponse, Response

from src.config import settings
from src.consumer import ReviewConsumer
from src.models.schemas import (
    ChatMetadata,
    ChatRequest,
    ChatResponse,
    Citation,
    FeedbackRequest,
    SuggestRequest,
    SuggestResponse,
    SummarizeRequest,
    SummarizeResponse,
)
from src.services.gemini_service import GeminiService
from src.services.mcp_tools import MCPTools
from src.services.rag_service import RAGService

logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL, logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

CHAT_REQUESTS = Counter("ai_chat_requests_total", "Total AI chat requests")
CHAT_LATENCY = Histogram("ai_chat_duration_seconds", "AI chat response latency")
REVIEWS_PROCESSED = Counter("ai_reviews_processed_total", "Total reviews processed")
FEEDBACK_COUNT = Counter("ai_feedback_total", "Total AI chat feedback received")

PUBLIC_PATHS = {"/health", "/ready", "/metrics", "/docs", "/openapi.json", "/redoc"}


gemini_service: GeminiService | None = None
rag_service: RAGService | None = None
mcp_tools: MCPTools | None = None
review_consumer: ReviewConsumer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown logic."""
    del app
    global gemini_service, rag_service, mcp_tools, review_consumer

    logger.info("Starting AI Worker service in environment=%s", settings.ENVIRONMENT)

    gemini_service = GeminiService()
    rag_service = RAGService(gemini_service)
    mcp_tools = MCPTools()

    if not mcp_tools.ping():
        raise RuntimeError("PostgreSQL is unreachable for MCP tools")

    qdrant_stats = rag_service.get_collection_stats()
    if "error" in qdrant_stats:
        raise RuntimeError(f"Qdrant is unavailable: {qdrant_stats['error']}")

    review_consumer = ReviewConsumer(gemini_service, rag_service)
    review_consumer.start()

    logger.info("AI Worker ready")
    yield

    logger.info("Shutting down AI Worker")
    if review_consumer:
        review_consumer.stop()


app = FastAPI(
    title="Univent AI Worker",
    description="AI/ML backend for Univent - sentiment analysis, RAG, and AI chat",
    version="1.0.0",
    lifespan=lifespan,
)

Instrumentator().instrument(app).expose(app)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def _is_public_path(path: str) -> bool:
    return path in PUBLIC_PATHS


def _service_state() -> dict:
    qdrant_stats = rag_service.get_collection_stats() if rag_service else {"error": "rag service unavailable"}
    postgres_ok = bool(mcp_tools and mcp_tools.ping())
    qdrant_ok = "error" not in qdrant_stats
    consumer_running = bool(review_consumer and getattr(review_consumer, "running", False))

    return {
        "gemini": "connected" if gemini_service else "disconnected",
        "postgres": "connected" if postgres_ok else "disconnected",
        "qdrant": qdrant_stats,
        "consumer": "running" if consumer_running else "stopped",
        "ready": bool(gemini_service and postgres_ok and qdrant_ok and consumer_running),
    }


@app.middleware("http")
async def add_request_context(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
    start_time = time.time()

    if not _is_public_path(request.url.path):
        token = request.headers.get("X-Internal-Token")
        if token != settings.INTERNAL_SHARED_SECRET:
            logger.warning(
                "Unauthorized internal access attempt path=%s request_id=%s",
                request.url.path,
                request_id,
            )
            return JSONResponse(content={"detail": "Forbidden"}, status_code=403)

    delay_ms = request.headers.get("X-Test-Delay")
    if delay_ms and delay_ms.isdigit() and settings.ENVIRONMENT in {"development", "test"}:
        await asyncio.sleep(int(delay_ms) / 1000.0)

    response = await call_next(request)
    duration_ms = int((time.time() - start_time) * 1000)
    response.headers["X-Request-ID"] = request_id

    logger.info(
        "request_completed method=%s path=%s status=%s duration_ms=%s request_id=%s",
        request.method,
        request.url.path,
        response.status_code,
        duration_ms,
        request_id,
    )
    return response


@app.get("/health")
async def health():
    return {"status": "healthy", "service": "ai-worker", "environment": settings.ENVIRONMENT}


@app.get("/ready")
async def ready():
    state = _service_state()
    status_code = 200 if state["ready"] else 503
    return JSONResponse(
        content={
            "status": "ready" if state["ready"] else "degraded",
            "service": "ai-worker",
            "environment": settings.ENVIRONMENT,
            "dependencies": state,
        },
        status_code=status_code,
    )


@app.get("/metrics")
async def metrics():
    return Response(content=generate_latest(), media_type="text/plain")


@app.post("/api/v1/ai/chat", response_model=ChatResponse)
async def ai_chat(request: ChatRequest):
    """AI-powered chat with RAG and MCP tool calling."""
    CHAT_REQUESTS.inc()
    start_time = time.time()

    try:
        if not rag_service or not mcp_tools or not gemini_service:
            raise HTTPException(status_code=503, detail="AI worker is not ready")

        rag_results = rag_service.search(
            query=request.query,
            college_id=request.college_id,
            program_id=request.program_id,
        )
        context_chunks = [result["chunk_text"] for result in rag_results]

        tool_results = {}
        tools_called = []

        if request.college_id:
            stats = mcp_tools.get_college_stats(request.college_id, request.program_id)
            if "error" not in stats:
                tool_results["college_stats"] = stats
                tools_called.append("get_college_stats")

        if request.college_id and request.program_id:
            roi = mcp_tools.get_roi(request.college_id, request.program_id)
            if "error" not in roi:
                tool_results["roi"] = roi
                tools_called.append("get_roi")

        matched_reviews = mcp_tools.search_reviews(
            query=request.query,
            college_id=request.college_id,
            program_id=request.program_id,
            limit=5,
        )
        if matched_reviews:
            tool_results["reviews"] = matched_reviews
            tools_called.append("search_reviews")

        response_text = gemini_service.generate_chat_response(
            query=request.query,
            context_chunks=context_chunks,
            tool_results=tool_results,
        )

        citations = []
        seen_reviews = set()
        for result in rag_results:
            if result["review_id"] in seen_reviews:
                continue

            citations.append(
                Citation(
                    review_id=result["review_id"],
                    excerpt=result["chunk_text"][:200],
                    rating=result.get("rating"),
                    verified=result.get("is_verified", False),
                    year=result.get("graduation_year"),
                )
            )
            seen_reviews.add(result["review_id"])

        verified_count = sum(1 for result in rag_results if result.get("is_verified"))
        reviews_used = len(seen_reviews)
        if reviews_used >= 8 and verified_count >= 3:
            confidence = "HIGH"
        elif reviews_used >= 4:
            confidence = "MEDIUM"
        else:
            confidence = "LOW"

        processing_time = int((time.time() - start_time) * 1000)
        CHAT_LATENCY.observe(processing_time / 1000)

        return ChatResponse(
            response=response_text,
            citations=citations,
            metadata=ChatMetadata(
                reviews_used=reviews_used,
                verified_count=verified_count,
                confidence=confidence,
                tools_called=tools_called,
                processing_time_ms=processing_time,
            ),
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("AI chat error: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail="AI processing failed") from exc


@app.post("/api/v1/ai/chat/feedback")
async def ai_chat_feedback(request: FeedbackRequest):
    """Receive feedback for AI chat responses."""
    FEEDBACK_COUNT.inc()
    logger.info("Feedback received for conversation %s: rating=%d", request.conversation_id, request.rating)
    return {"status": "success"}


@app.post("/api/v1/ai/summarize", response_model=SummarizeResponse)
async def ai_summarize(request: SummarizeRequest):
    """Generate AI summary of all reviews for a college."""
    try:
        if not mcp_tools:
            raise HTTPException(status_code=503, detail="AI worker is not ready")

        reviews = mcp_tools.search_reviews(
            college_id=request.college_id,
            program_id=request.program_id,
            limit=20,
        )
        if not reviews:
            raise HTTPException(status_code=404, detail="No reviews found for this college")

        review_texts = [review["excerpt"] for review in reviews]
        college_name = reviews[0].get("college_name", "Unknown")
        program_name = reviews[0].get("program_name")
        result = gemini_service.generate_summary(review_texts, college_name, program_name)

        return SummarizeResponse(
            summary=result.get("summary", ""),
            strengths=result.get("strengths", []),
            weaknesses=result.get("weaknesses", []),
            reviews_analyzed=len(reviews),
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Summarize error: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail="Summarization failed") from exc


@app.post("/api/v1/ai/suggest", response_model=SuggestResponse)
async def ai_suggest(request: SuggestRequest):
    """Get AI-powered college recommendations based on preferences."""
    try:
        if not mcp_tools:
            raise HTTPException(status_code=503, detail="AI worker is not ready")

        recommendations = mcp_tools.get_recommendations(
            program_category=request.program_category,
            location=request.location,
            limit=10,
        )
        return SuggestResponse(
            recommendations=recommendations,
            reasoning=f"Found {len(recommendations)} colleges matching your criteria.",
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Suggest error: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail="Suggestion failed") from exc


@app.get("/api/v1/ai/stats")
async def ai_stats():
    """Get AI service statistics."""
    return {
        "service": "ai-worker",
        "environment": settings.ENVIRONMENT,
        "dependencies": _service_state(),
        "models": {
            "flash": settings.GEMINI_FLASH_MODEL,
            "pro": settings.GEMINI_PRO_MODEL,
            "embedding": settings.GEMINI_EMBEDDING_MODEL,
        },
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("src.main:app", host=settings.HOST, port=settings.PORT, reload=False)
