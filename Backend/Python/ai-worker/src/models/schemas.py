"""Pydantic models for review processing and AI chat."""
from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from datetime import datetime
from enum import Enum


# ─── Kafka Events ────────────────────────────────────

class ReviewSubmittedEvent(BaseModel):
    review_id: str
    college_id: str
    program_id: str
    review_text: str
    pros: List[str] = Field(default_factory=list)
    cons: List[str] = Field(default_factory=list)
    user_id: str
    is_verified: bool = False
    graduation_year: Optional[int] = None
    overall_rating: Optional[int] = None
    timestamp: datetime


class ReviewProcessedEvent(BaseModel):
    review_id: str
    sentiment: str  # POSITIVE, NEUTRAL, NEGATIVE
    sentiment_score: float
    extracted_topics: List[str]
    moderation_result: "ModerationResult"
    qdrant_indexed: bool
    processing_time_ms: int


class ModerationResult(BaseModel):
    safe: bool = True
    categories: List[str] = Field(default_factory=list)
    reason: Optional[str] = None


# ─── AI Chat ─────────────────────────────────────────

class ChatRequest(BaseModel):
    query: str = Field(..., min_length=3, max_length=500)
    college_id: Optional[str] = None
    program_id: Optional[str] = None
    conversation_id: Optional[str] = None


class ChatResponse(BaseModel):
    response: str
    citations: List["Citation"] = Field(default_factory=list)
    metadata: "ChatMetadata"


class Citation(BaseModel):
    review_id: str
    excerpt: str
    rating: Optional[int] = None
    verified: bool = False
    year: Optional[int] = None


class ChatMetadata(BaseModel):
    reviews_used: int = 0
    verified_count: int = 0
    confidence: str = "LOW"  # LOW, MEDIUM, HIGH
    tools_called: List[str] = Field(default_factory=list)
    processing_time_ms: int = 0


# ─── Summarize ───────────────────────────────────────

class SummarizeRequest(BaseModel):
    college_id: str
    program_id: Optional[str] = None


class SummarizeResponse(BaseModel):
    summary: str
    strengths: List[str] = Field(default_factory=list)
    weaknesses: List[str] = Field(default_factory=list)
    reviews_analyzed: int = 0


# ─── Suggest ─────────────────────────────────────────

class SuggestRequest(BaseModel):
    budget: Optional[str] = None
    location: Optional[str] = None
    focus: Optional[str] = None  # placements, research, campus_life
    program_category: Optional[str] = None


class SuggestResponse(BaseModel):
    recommendations: List["CollegeRecommendation"] = Field(default_factory=list)
    reasoning: str = ""


class CollegeRecommendation(BaseModel):
    college_id: str
    college_name: str
    program_name: Optional[str] = None
    match_score: float = 0.0
    highlights: List[str] = Field(default_factory=list)


# Forward references
ReviewProcessedEvent.model_rebuild()
ChatResponse.model_rebuild()
SuggestResponse.model_rebuild()
