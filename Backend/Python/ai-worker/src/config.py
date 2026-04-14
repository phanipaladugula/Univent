import os
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8000

    # Gemini API
    GEMINI_API_KEY: str = ""
    GEMINI_FLASH_MODEL: str = "gemini-2.0-flash"
    GEMINI_PRO_MODEL: str = "gemini-2.5-pro-preview-05-06"
    GEMINI_EMBEDDING_MODEL: str = "text-embedding-004"

    # PostgreSQL
    POSTGRES_URL: str = "postgresql://postgres:univent_dev_pass@localhost:5432/univent"

    # Qdrant Vector DB
    QDRANT_HOST: str = "localhost"
    QDRANT_PORT: int = 6333
    QDRANT_COLLECTION: str = "univent_reviews"

    # Kafka
    KAFKA_BROKERS: str = "localhost:9092"
    KAFKA_GROUP_ID: str = "ai-worker-group"

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
