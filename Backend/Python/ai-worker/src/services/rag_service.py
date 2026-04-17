"""RAG Pipeline: indexing and querying review chunks in Qdrant."""
import datetime
import hashlib
import logging
import re
from typing import List

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, FieldCondition, Filter, MatchValue, PointStruct, VectorParams

from src.config import settings
from src.services.gemini_service import GeminiService

logger = logging.getLogger(__name__)


class RAGService:
    def __init__(self, gemini: GeminiService):
        self.gemini = gemini
        self.client = self._create_client()
        self.collection = settings.QDRANT_COLLECTION
        self._ensure_collection()
        logger.info("RAG service initialized for collection=%s", self.collection)

    def _create_client(self) -> QdrantClient:
        if settings.QDRANT_URL:
            return QdrantClient(url=settings.QDRANT_URL, api_key=settings.QDRANT_API_KEY)
        return QdrantClient(
            host=settings.QDRANT_HOST,
            port=settings.QDRANT_PORT,
            api_key=settings.QDRANT_API_KEY,
        )

    def _ensure_collection(self):
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

    def index_review(
        self,
        review_id: str,
        college_id: str,
        program_id: str,
        review_text: str,
        sentiment: str,
        rating: int,
        is_verified: bool,
        graduation_year: int = None,
    ) -> int:
        chunks = self._chunk_text(review_text)
        if not chunks:
            return 0

        points = []
        for index, chunk in enumerate(chunks):
            try:
                vector = self.gemini.generate_embedding(chunk)
                points.append(
                    PointStruct(
                        id=self._point_id(review_id, index),
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
                            "chunk_index": index,
                        },
                    )
                )
            except Exception as exc:
                logger.warning("Failed to embed chunk %s for review %s: %s", index, review_id, exc)

        if points:
            self.client.upsert(collection_name=self.collection, points=points)
            logger.info("Indexed %s chunks for review %s", len(points), review_id)

        return len(points)

    def search(self, query: str, college_id: str = None, program_id: str = None, top_k: int = None) -> List[dict]:
        top_k = max(1, top_k or settings.RAG_TOP_K)
        query_vector = self.gemini.generate_query_embedding(query)

        conditions = []
        if college_id:
            conditions.append(FieldCondition(key="college_id", match=MatchValue(value=college_id)))
        if program_id:
            conditions.append(FieldCondition(key="program_id", match=MatchValue(value=program_id)))

        search_filter = Filter(must=conditions) if conditions else None

        results = self.client.search(
            collection_name=self.collection,
            query_vector=query_vector,
            query_filter=search_filter,
            limit=top_k,
            with_payload=True,
        )
        return self._rerank(results)[: settings.RAG_RERANK_TOP]

    def _rerank(self, results) -> List[dict]:
        current_year = datetime.datetime.now().year
        scored = []

        for hit in results:
            payload = hit.payload or {}
            similarity = float(hit.score or 0)
            trust_weight = 2.0 if payload.get("is_verified", False) else 1.0

            grad_year = payload.get("graduation_year", 0)
            if grad_year > 0:
                years_ago = max(0, current_year - grad_year)
                recency_weight = max(0.5, 1.0 - (years_ago * 0.1))
            else:
                recency_weight = 0.7

            scored.append(
                {
                    "chunk_text": payload.get("chunk_text", ""),
                    "review_id": payload.get("review_id", ""),
                    "college_id": payload.get("college_id", ""),
                    "program_id": payload.get("program_id", ""),
                    "rating": payload.get("rating", 0),
                    "sentiment": payload.get("sentiment", ""),
                    "is_verified": payload.get("is_verified", False),
                    "graduation_year": payload.get("graduation_year", 0),
                    "similarity": similarity,
                    "final_score": similarity * trust_weight * recency_weight,
                }
            )

        scored.sort(key=lambda item: item["final_score"], reverse=True)
        return scored

    def _chunk_text(self, text: str) -> List[str]:
        if not text or len(text.strip()) < 20:
            return []

        sentences = [sentence.strip() for sentence in re.split(r"(?<=[.!?])\s+", text.strip()) if sentence.strip()]
        if not sentences:
            return [text.strip()]

        chunks = []
        current_chunk = []
        current_length = 0

        for sentence in sentences:
            word_count = len(sentence.split())
            if current_length + word_count > settings.CHUNK_SIZE and current_chunk:
                chunks.append(" ".join(current_chunk))
                overlap_words = " ".join(current_chunk).split()[-settings.CHUNK_OVERLAP :]
                current_chunk = [" ".join(overlap_words), sentence] if overlap_words else [sentence]
                current_length = len(" ".join(current_chunk).split())
            else:
                current_chunk.append(sentence)
                current_length += word_count

        if current_chunk:
            chunks.append(" ".join(current_chunk))

        return [chunk.strip() for chunk in chunks if chunk.strip()] or [text.strip()]

    def get_collection_stats(self) -> dict:
        try:
            info = self.client.get_collection(self.collection)
            return {
                "collection": self.collection,
                "points_count": info.points_count,
                "vectors_count": info.vectors_count,
                "status": info.status.value if info.status else "unknown",
            }
        except Exception as exc:
            logger.error("Failed to fetch collection stats: %s", exc)
            return {"error": str(exc)}

    @staticmethod
    def _point_id(review_id: str, index: int) -> str:
        digest = hashlib.sha1(f"{review_id}:{index}".encode("utf-8")).hexdigest()
        return digest
