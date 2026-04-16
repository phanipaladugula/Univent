"""FastAPI application entry point for the AI Worker service."""
import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import Counter, Histogram, generate_latest
from prometheus_fastapi_instrumentator import Instrumentator
from starlette.responses import Response

from src.config import settings
from src.consumer import ReviewConsumer
from src.models.schemas import (
    ChatMetadata,
    ChatRequest,
    ChatResponse,
    Citation,
    SuggestRequest,
    SuggestResponse,
    SummarizeRequest,
    SummarizeResponse,
)
from src.services.gemini_service import GeminiService
from src.services.mcp_tools import MCPTools
from src.services.rag_service import RAGService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

CHAT_REQUESTS = Counter("ai_chat_requests_total", "Total AI chat requests")
CHAT_LATENCY = Histogram("ai_chat_duration_seconds", "AI chat response latency")
REVIEWS_PROCESSED = Counter("ai_reviews_processed_total", "Total reviews processed")

gemini_service: GeminiService = None
rag_service: RAGService = None
mcp_tools: MCPTools = None
review_consumer: ReviewConsumer = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown logic."""
    del app
    global gemini_service, rag_service, mcp_tools, review_consumer

    logger.info("Starting AI Worker service")

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
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def add_request_context(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
    start_time = time.time()

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
    qdrant_stats = rag_service.get_collection_stats() if rag_service else {}
    postgres_status = "connected" if mcp_tools and mcp_tools.ping() else "disconnected"
    return {
        "status": "healthy",
        "service": "ai-worker",
        "gemini": "connected" if gemini_service else "disconnected",
        "postgres": postgres_status,
        "qdrant": qdrant_stats,
    }


@app.get("/metrics")
async def metrics():
    return Response(content=generate_latest(), media_type="text/plain")


@app.post("/api/v1/ai/chat", response_model=ChatResponse)
async def ai_chat(request: ChatRequest):
    """AI-powered chat with RAG and MCP tool calling."""
    CHAT_REQUESTS.inc()
    start_time = time.time()

    try:
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
        confidence = "HIGH" if reviews_used >= 40 else "MEDIUM" if reviews_used >= 10 else "LOW"

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
    except Exception as exc:
        logger.error("AI chat error: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail=f"AI processing failed: {exc}") from exc


@app.post("/api/v1/ai/summarize", response_model=SummarizeResponse)
async def ai_summarize(request: SummarizeRequest):
    """Generate AI summary of all reviews for a college."""
    try:
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
        raise HTTPException(status_code=500, detail=f"Summarization failed: {exc}") from exc


@app.post("/api/v1/ai/suggest", response_model=SuggestResponse)
async def ai_suggest(request: SuggestRequest):
    """Get AI-powered college recommendations based on preferences."""
    try:
        recommendations = mcp_tools.get_recommendations(
            program_category=request.program_category,
            location=request.location,
            limit=10,
        )
        return SuggestResponse(
            recommendations=recommendations,
            reasoning=f"Found {len(recommendations)} colleges matching your criteria.",
        )
    except Exception as exc:
        logger.error("Suggest error: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail=f"Suggestion failed: {exc}") from exc


@app.get("/api/v1/ai/stats")
async def ai_stats():
    """Get AI service statistics."""
    return {
        "qdrant": rag_service.get_collection_stats(),
        "models": {
            "flash": settings.GEMINI_FLASH_MODEL,
            "pro": settings.GEMINI_PRO_MODEL,
            "embedding": settings.GEMINI_EMBEDDING_MODEL,
        },
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("src.main:app", host=settings.HOST, port=settings.PORT, reload=True)
