"""FastAPI application entry point for the AI Worker service."""
import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import Counter, Histogram, generate_latest
from starlette.responses import Response

from src.config import settings
from src.models.schemas import (
    ChatRequest, ChatResponse, ChatMetadata, Citation,
    SummarizeRequest, SummarizeResponse,
    SuggestRequest, SuggestResponse,
)
from src.services.gemini_service import GeminiService
from src.services.rag_service import RAGService
from src.services.mcp_tools import MCPTools
from src.consumer import ReviewConsumer

# ─── Logging ──────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

# ─── Prometheus Metrics ───────────────────────────────
CHAT_REQUESTS = Counter("ai_chat_requests_total", "Total AI chat requests")
CHAT_LATENCY = Histogram("ai_chat_duration_seconds", "AI chat response latency")
REVIEWS_PROCESSED = Counter("ai_reviews_processed_total", "Total reviews processed")

# ─── Service Instances ────────────────────────────────
gemini_service: GeminiService = None
rag_service: RAGService = None
mcp_tools: MCPTools = None
review_consumer: ReviewConsumer = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown logic."""
    global gemini_service, rag_service, mcp_tools, review_consumer

    logger.info("🚀 Starting AI Worker service")

    # Initialize services
    gemini_service = GeminiService()
    rag_service = RAGService(gemini_service)
    mcp_tools = MCPTools()

    # Start Kafka consumer
    review_consumer = ReviewConsumer(gemini_service, rag_service)
    review_consumer.start()

    logger.info("✅ AI Worker ready")
    yield

    # Shutdown
    logger.info("🛑 Shutting down AI Worker")
    review_consumer.stop()


app = FastAPI(
    title="Univent AI Worker",
    description="AI/ML backend for Univent - sentiment analysis, RAG, and AI chat",
    version="1.0.0",
    lifespan=lifespan,
)

from prometheus_fastapi_instrumentator import Instrumentator
Instrumentator().instrument(app).expose(app)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─── Health ───────────────────────────────────────────

@app.get("/health")
async def health():
    qdrant_stats = rag_service.get_collection_stats() if rag_service else {}
    return {
        "status": "healthy",
        "service": "ai-worker",
        "gemini": "connected" if gemini_service else "disconnected",
        "qdrant": qdrant_stats,
    }


@app.get("/metrics")
async def metrics():
    return Response(content=generate_latest(), media_type="text/plain")


# ─── AI Chat ──────────────────────────────────────────

@app.post("/api/v1/ai/chat", response_model=ChatResponse)
async def ai_chat(request: ChatRequest):
    """AI-powered chat with RAG and MCP tool calling."""
    CHAT_REQUESTS.inc()
    start_time = time.time()

    try:
        # Step 1: Search Qdrant for relevant review chunks
        rag_results = rag_service.search(
            query=request.query,
            college_id=request.college_id,
            program_id=request.program_id,
        )
        context_chunks = [r["chunk_text"] for r in rag_results]

        # Step 2: Call MCP tools for real-time data
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

        # Also search reviews via MCP for structured data
        matched_reviews = mcp_tools.search_reviews(
            query=request.query,
            college_id=request.college_id,
            program_id=request.program_id,
            limit=5,
        )
        if matched_reviews:
            tool_results["reviews"] = matched_reviews
            tools_called.append("search_reviews")

        # Step 3: Generate response with Gemini
        response_text = gemini_service.generate_chat_response(
            query=request.query,
            context_chunks=context_chunks,
            tool_results=tool_results,
        )

        # Step 4: Build citations from RAG results
        citations = []
        seen_reviews = set()
        for r in rag_results:
            if r["review_id"] not in seen_reviews:
                citations.append(Citation(
                    review_id=r["review_id"],
                    excerpt=r["chunk_text"][:200],
                    rating=r.get("rating"),
                    verified=r.get("is_verified", False),
                    year=r.get("graduation_year"),
                ))
                seen_reviews.add(r["review_id"])

        # Metadata
        verified_count = sum(1 for r in rag_results if r.get("is_verified"))
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

    except Exception as e:
        logger.error("AI chat error: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=f"AI processing failed: {str(e)}")


# ─── Summarize ────────────────────────────────────────

@app.post("/api/v1/ai/summarize", response_model=SummarizeResponse)
async def ai_summarize(request: SummarizeRequest):
    """Generate AI summary of all reviews for a college."""
    try:
        # Get reviews from DB
        reviews = mcp_tools.search_reviews(
            college_id=request.college_id,
            program_id=request.program_id,
            limit=20,
        )

        if not reviews:
            raise HTTPException(status_code=404, detail="No reviews found for this college")

        review_texts = [r["excerpt"] for r in reviews]
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
    except Exception as e:
        logger.error("Summarize error: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=f"Summarization failed: {str(e)}")


# ─── Suggest ──────────────────────────────────────────

@app.post("/api/v1/ai/suggest", response_model=SuggestResponse)
async def ai_suggest(request: SuggestRequest):
    """Get AI-powered college recommendations based on preferences."""
    try:
        # Get top colleges from rankings
        import psycopg2
        import psycopg2.extras
        conn = psycopg2.connect(settings.POSTGRES_URL, cursor_factory=psycopg2.extras.RealDictCursor)
        cur = conn.cursor()

        sql = """
            SELECT DISTINCT ON (college_id)
                   college_id, college_name, program_name, city, state,
                   overall_rating, placement_percentage, median_package,
                   total_fees, weighted_score
            FROM mv_college_rankings
            WHERE review_count > 0
        """
        params = []

        if request.program_category:
            sql += " AND category = %s"
            params.append(request.program_category)

        if request.location:
            sql += " AND (LOWER(state) LIKE %s OR LOWER(city) LIKE %s)"
            params.extend([f"%{request.location.lower()}%", f"%{request.location.lower()}%"])

        sql += " ORDER BY college_id, weighted_score DESC LIMIT 10"

        cur.execute(sql, params)
        rows = cur.fetchall()
        conn.close()

        recommendations = []
        for row in rows:
            highlights = []
            if row.get("overall_rating") and float(row["overall_rating"]) >= 4:
                highlights.append(f"High rating: {row['overall_rating']}/5")
            if row.get("placement_percentage") and float(row["placement_percentage"]) >= 80:
                highlights.append(f"Strong placements: {row['placement_percentage']}%")
            if row.get("median_package"):
                pkg_lpa = float(row["median_package"]) / 100000
                highlights.append(f"Median package: ₹{pkg_lpa:.1f} LPA")

            recommendations.append({
                "college_id": str(row["college_id"]),
                "college_name": row["college_name"],
                "program_name": row.get("program_name"),
                "match_score": float(row.get("weighted_score", 0)),
                "highlights": highlights,
            })

        return SuggestResponse(
            recommendations=recommendations,
            reasoning=f"Found {len(recommendations)} colleges matching your criteria.",
        )

    except Exception as e:
        logger.error("Suggest error: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=f"Suggestion failed: {str(e)}")


# ─── RAG Stats (admin) ───────────────────────────────

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
