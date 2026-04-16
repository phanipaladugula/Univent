from types import SimpleNamespace

from src.services.rag_service import RAGService


class DummyGemini:
    def generate_embedding(self, text: str):
        return [0.1, 0.2, 0.3]

    def generate_query_embedding(self, query: str):
        return [0.1, 0.2, 0.3]


class DummyClient:
    def __init__(self):
        self.points = []

    def get_collections(self):
        return SimpleNamespace(collections=[])

    def create_collection(self, **kwargs):
        return None

    def upsert(self, collection_name, points):
        self.points.extend(points)

    def get_collection(self, name):
        return SimpleNamespace(points_count=len(self.points), vectors_count=len(self.points), status=SimpleNamespace(value="green"))


def build_service(monkeypatch):
    monkeypatch.setattr(RAGService, "_create_client", lambda self: DummyClient())
    return RAGService(DummyGemini())


def test_chunk_text_creates_multiple_chunks(monkeypatch):
    service = build_service(monkeypatch)
    text = "Sentence one is long enough to matter. Sentence two adds more review detail. Sentence three keeps the chunking logic working."

    chunks = service._chunk_text(text)

    assert chunks
    assert all(chunk.strip() for chunk in chunks)


def test_rerank_prioritizes_verified_reviews(monkeypatch):
    service = build_service(monkeypatch)
    results = [
        SimpleNamespace(score=0.8, payload={"chunk_text": "a", "review_id": "1", "is_verified": False, "graduation_year": 2024}),
        SimpleNamespace(score=0.7, payload={"chunk_text": "b", "review_id": "2", "is_verified": True, "graduation_year": 2024}),
    ]

    ranked = service._rerank(results)

    assert ranked[0]["review_id"] == "2"
