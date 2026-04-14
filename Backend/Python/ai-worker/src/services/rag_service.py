"""RAG Pipeline: indexing and querying review chunks in Qdrant."""
import logging
import re
from typing import List, Optional, Tuple

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct, Filter, FieldCondition, MatchValue

from src.config import settings
from src.services.gemini_service import GeminiService

logger = logging.getLogger(__name__)


class RAGService:
    def __init__(self, gemini: GeminiService):
        self.gemini = gemini
        self.client = QdrantClient(host=settings.QDRANT_HOST, port=settings.QDRANT_PORT)
        self.collection = settings.QDRANT_COLLECTION
        self._ensure_collection()
        logger.info("✅ RAG service initialized (Qdrant: %s:%d)", settings.QDRANT_HOST, settings.QDRANT_PORT)

    def _ensure_collection(self):
        """Create collection if it doesn't exist."""
        collections = [c.name for c in self.client.get_collections().collections]
        if self.collection not in collections:
            self.client.create_collection(
                collection_name=self.collection,
                vectors_config=VectorParams(
                    size=settings.EMBEDDING_DIM,
                    distance=Distance.COSINE,
                ),
            )
            logger.info("Created Qdrant collection: %s", self.collection)

    def index_review(self, review_id: str, college_id: str, program_id: str,
                     review_text: str, sentiment: str, rating: int,
                     is_verified: bool, graduation_year: int = None) -> int:
        """Chunk, embed, and index a review into Qdrant. Returns number of chunks indexed."""
        chunks = self._chunk_text(review_text)
        if not chunks:
            return 0

        points = []
        for i, chunk in enumerate(chunks):
            try:
                vector = self.gemini.generate_embedding(chunk)
                point = PointStruct(
                    id=f"{review_id}-{i}",
                    vector=vector,
                    payload={
                        "review_id": review_id,
                        "college_id": college_id,
                        "program_id": program_id,
                        "sentiment": sentiment,
                        "rating": rating,
                        "is_verified": is_verified,
                        "graduation_year": graduation_year or 0,
                        "chunk_text": chunk,
                        "chunk_index": i,
                    },
                )
                points.append(point)
            except Exception as e:
                logger.warning("Failed to embed chunk %d for review %s: %s", i, review_id, e)

        if points:
            self.client.upsert(collection_name=self.collection, points=points)
            logger.info("Indexed %d chunks for review %s", len(points), review_id)

        return len(points)

    def search(self, query: str, college_id: str = None, program_id: str = None,
               top_k: int = None) -> List[dict]:
        """Search for relevant review chunks using semantic similarity."""
        top_k = top_k or settings.RAG_TOP_K

        query_vector = self.gemini.generate_query_embedding(query)

        # Build filters
        conditions = []
        if college_id:
            conditions.append(FieldCondition(
                key="college_id", match=MatchValue(value=college_id)))
        if program_id:
            conditions.append(FieldCondition(
                key="program_id", match=MatchValue(value=program_id)))

        search_filter = Filter(must=conditions) if conditions else None

        results = self.client.search(
            collection_name=self.collection,
            query_vector=query_vector,
            query_filter=search_filter,
            limit=top_k,
            with_payload=True,
        )

        # Rerank by trust + recency
        ranked_results = self._rerank(results)

        return ranked_results[:settings.RAG_RERANK_TOP]

    def _rerank(self, results) -> List[dict]:
        """Rerank search results by trust and recency."""
        import datetime
        current_year = datetime.datetime.now().year

        scored = []
        for hit in results:
            payload = hit.payload
            similarity = hit.score

            # Trust weight: verified reviews get 2x
            trust_weight = 2.0 if payload.get("is_verified", False) else 1.0

            # Recency weight: newer reviews are more relevant
            grad_year = payload.get("graduation_year", 0)
            if grad_year > 0:
                years_ago = max(0, current_year - grad_year)
                recency_weight = max(0.5, 1.0 - (years_ago * 0.1))
            else:
                recency_weight = 0.7

            final_score = similarity * trust_weight * recency_weight

            scored.append({
                "chunk_text": payload.get("chunk_text", ""),
                "review_id": payload.get("review_id", ""),
                "college_id": payload.get("college_id", ""),
                "program_id": payload.get("program_id", ""),
                "rating": payload.get("rating", 0),
                "sentiment": payload.get("sentiment", ""),
                "is_verified": payload.get("is_verified", False),
                "graduation_year": payload.get("graduation_year", 0),
                "similarity": similarity,
                "final_score": final_score,
            })

        scored.sort(key=lambda x: x["final_score"], reverse=True)
        return scored

    def _chunk_text(self, text: str) -> List[str]:
        """Split review text into semantic chunks."""
        if not text or len(text.strip()) < 20:
            return []

        # Split by sentences
        sentences = re.split(r'(?<=[.!?])\s+', text.strip())

        chunks = []
        current_chunk = []
        current_length = 0

        for sentence in sentences:
            word_count = len(sentence.split())
            if current_length + word_count > settings.CHUNK_SIZE and current_chunk:
                chunks.append(" ".join(current_chunk))
                # Overlap: keep last sentence
                overlap_sentence = current_chunk[-1] if current_chunk else ""
                current_chunk = [overlap_sentence, sentence] if overlap_sentence else [sentence]
                current_length = len(overlap_sentence.split()) + word_count
            else:
                current_chunk.append(sentence)
                current_length += word_count

        if current_chunk:
            chunks.append(" ".join(current_chunk))

        # If text is very short, just use the whole text as one chunk
        if not chunks:
            chunks = [text.strip()]

        return chunks

    def get_collection_stats(self) -> dict:
        """Get collection statistics for monitoring."""
        try:
            info = self.client.get_collection(self.collection)
            return {
                "collection": self.collection,
                "points_count": info.points_count,
                "vectors_count": info.vectors_count,
                "status": info.status.value if info.status else "unknown",
            }
        except Exception as e:
            return {"error": str(e)}
