import os
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8000

    # Gemini API (REQUIRED — will fail at startup if missing)
    GEMINI_API_KEY: str
    GEMINI_FLASH_MODEL: str = "gemini-2.0-flash"
    GEMINI_PRO_MODEL: str = "gemini-2.5-pro-preview-05-06"
    GEMINI_EMBEDDING_MODEL: str = "text-embedding-004"

    # PostgreSQL (REQUIRED)
    POSTGRES_URL: str

    # Qdrant Vector DB
    QDRANT_URL: Optional[str] = None
    QDRANT_HOST: str = "qdrant"
    QDRANT_PORT: int = 6333
    QDRANT_API_KEY: Optional[str] = None
    QDRANT_COLLECTION: str = "univent_reviews"

    # Kafka (REQUIRED)
    KAFKA_BROKERS: str
    KAFKA_SECURITY_PROTOCOL: str = "PLAINTEXT"
    KAFKA_SASL_MECHANISM: str = "SCRAM-SHA-256"
    KAFKA_USERNAME: Optional[str] = None
    KAFKA_PASSWORD: Optional[str] = None

    KAFKA_GROUP_ID: str = "ai-worker-group"

    # Connection safety
    POSTGRES_CONNECT_TIMEOUT: int = 5
    POSTGRES_STATEMENT_TIMEOUT_MS: int = 10000

    # Processing
    CHUNK_SIZE: int = 200  # tokens per chunk
    CHUNK_OVERLAP: int = 30
    RAG_TOP_K: int = 10
    RAG_RERANK_TOP: int = 5
    EMBEDDING_DIM: int = 768

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
