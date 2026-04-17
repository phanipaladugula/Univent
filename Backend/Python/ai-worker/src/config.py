from typing import List, Optional

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    ENVIRONMENT: str = "production"
    LOG_LEVEL: str = "INFO"

    # Security
    INTERNAL_SHARED_SECRET: str
    ALLOWED_ORIGINS_RAW: str = Field(default="http://localhost", alias="ALLOWED_ORIGINS")

    # Gemini API (required)
    GEMINI_API_KEY: str
    GEMINI_FLASH_MODEL: str = "gemini-2.0-flash"
    GEMINI_PRO_MODEL: str = "gemini-2.5-pro-preview-05-06"
    GEMINI_EMBEDDING_MODEL: str = "text-embedding-004"

    # PostgreSQL (required)
    POSTGRES_URL: str

    # Qdrant Vector DB
    QDRANT_URL: Optional[str] = None
    QDRANT_HOST: str = "qdrant"
    QDRANT_PORT: int = 6333
    QDRANT_API_KEY: Optional[str] = None
    QDRANT_COLLECTION: str = "univent_reviews"

    # Kafka (required)
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
    CHUNK_SIZE: int = 200
    CHUNK_OVERLAP: int = 30
    RAG_TOP_K: int = 10
    RAG_RERANK_TOP: int = 5
    EMBEDDING_DIM: int = 768

    @field_validator("ENVIRONMENT")
    @classmethod
    def validate_environment(cls, value: str) -> str:
        normalized = value.strip().lower()
        allowed = {"development", "test", "staging", "production"}
        if normalized not in allowed:
            raise ValueError(f"ENVIRONMENT must be one of {sorted(allowed)}")
        return normalized

    @field_validator("LOG_LEVEL")
    @classmethod
    def validate_log_level(cls, value: str) -> str:
        normalized = value.strip().upper()
        allowed = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}
        if normalized not in allowed:
            raise ValueError(f"LOG_LEVEL must be one of {sorted(allowed)}")
        return normalized

    @property
    def allowed_origins(self) -> List[str]:
        return [origin.strip() for origin in self.ALLOWED_ORIGINS_RAW.split(",") if origin.strip()]


settings = Settings()
