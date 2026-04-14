"""Gemini API wrapper for sentiment analysis, topic extraction, and moderation."""
import json
import logging
import time
from typing import List, Tuple, Optional

import google.generativeai as genai
from tenacity import retry, stop_after_attempt, wait_exponential

from src.config import settings

logger = logging.getLogger(__name__)


class GeminiService:
    def __init__(self):
        genai.configure(api_key=settings.GEMINI_API_KEY)
        self.flash_model = genai.GenerativeModel(settings.GEMINI_FLASH_MODEL)
        self.pro_model = genai.GenerativeModel(settings.GEMINI_PRO_MODEL)
        logger.info("✅ Gemini service initialized (flash=%s, pro=%s)",
                     settings.GEMINI_FLASH_MODEL, settings.GEMINI_PRO_MODEL)

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
    def analyze_sentiment(self, review_text: str, pros: List[str], cons: List[str]) -> Tuple[str, float]:
        """Analyze sentiment of a review. Returns (sentiment, score)."""
        prompt = f"""Analyze the sentiment of this college review. 
Return ONLY a JSON object with exactly these fields:
- "sentiment": one of "POSITIVE", "NEUTRAL", or "NEGATIVE"
- "score": a float between 0.0 and 1.0 (0=very negative, 1=very positive)

Review text: {review_text[:2000]}
Pros: {', '.join(pros[:5])}
Cons: {', '.join(cons[:5])}

Respond with ONLY the JSON, no markdown, no explanation."""

        start = time.time()
        response = self.flash_model.generate_content(prompt)
        latency = (time.time() - start) * 1000

        try:
            text = response.text.strip()
            # Strip markdown code fences if present
            if text.startswith("```"):
                text = text.split("\n", 1)[1].rsplit("```", 1)[0].strip()
            result = json.loads(text)
            sentiment = result.get("sentiment", "NEUTRAL")
            score = float(result.get("score", 0.5))
            logger.info("Sentiment analyzed: %s (%.2f) in %.0fms", sentiment, score, latency)
            return sentiment, score
        except (json.JSONDecodeError, KeyError, ValueError) as e:
            logger.warning("Failed to parse sentiment response: %s", e)
            return "NEUTRAL", 0.5

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
    def extract_topics(self, review_text: str) -> List[str]:
        """Extract key topics from a review."""
        prompt = f"""Extract the key topics discussed in this college review.
Return ONLY a JSON array of topic strings (max 8 topics).
Topics should be short phrases like: "placements", "faculty quality", "hostel food", 
"lab facilities", "campus life", "fees", "library", "sports", etc.

Review: {review_text[:2000]}

Respond with ONLY the JSON array, no markdown, no explanation."""

        start = time.time()
        response = self.flash_model.generate_content(prompt)
        latency = (time.time() - start) * 1000

        try:
            text = response.text.strip()
            if text.startswith("```"):
                text = text.split("\n", 1)[1].rsplit("```", 1)[0].strip()
            topics = json.loads(text)
            if isinstance(topics, list):
                topics = [str(t) for t in topics[:8]]
                logger.info("Extracted %d topics in %.0fms", len(topics), latency)
                return topics
        except (json.JSONDecodeError, ValueError) as e:
            logger.warning("Failed to parse topics response: %s", e)

        return []

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
    def check_moderation(self, review_text: str) -> dict:
        """Check if review content is appropriate. Returns moderation result."""
        prompt = f"""You are a content moderator for a college review platform.
Check if this review contains any of these violations:
- Hate speech or discrimination
- Personal attacks on specific individuals (naming professors/staff)
- Excessive profanity
- Spam or promotional content
- Completely off-topic content (not about the college/program)

Return ONLY a JSON object:
- "safe": true if the content is appropriate, false if it violates rules
- "categories": array of violated categories (empty if safe)
- "reason": brief explanation if not safe (null if safe)

Review: {review_text[:2000]}

Respond with ONLY the JSON, no markdown."""

        start = time.time()
        response = self.flash_model.generate_content(prompt)
        latency = (time.time() - start) * 1000

        try:
            text = response.text.strip()
            if text.startswith("```"):
                text = text.split("\n", 1)[1].rsplit("```", 1)[0].strip()
            result = json.loads(text)
            logger.info("Moderation check: safe=%s in %.0fms", result.get("safe", True), latency)
            return {
                "safe": result.get("safe", True),
                "categories": result.get("categories", []),
                "reason": result.get("reason"),
            }
        except (json.JSONDecodeError, ValueError) as e:
            logger.warning("Failed to parse moderation response: %s", e)
            return {"safe": True, "categories": [], "reason": None}

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
    def generate_chat_response(self, query: str, context_chunks: List[str],
                                tool_results: dict, conversation_history: List[dict] = None) -> str:
        """Generate a grounded chat response using RAG context and tool results."""
        system_prompt = """You are an AI assistant for Univent, a college review platform in India.
You MUST answer ONLY based on the provided student review context and tool results.
If you don't have enough information, say "I don't have enough reviews to answer this accurately."

Rules:
- Always cite how many reviews your answer is based on
- Mention if reviews are from verified students
- Use specific data from tool results (ratings, packages, placement %)
- Never make up statistics or facts
- Be balanced — mention both pros and cons
- Use Indian context (₹ for currency, LPA for salary)"""

        user_prompt = f"""Student Review Context:
{chr(10).join(context_chunks[:5])}

Tool Results:
{json.dumps(tool_results, indent=2, default=str) if tool_results else "No tool data available"}

User Question: {query}

Provide a helpful, grounded response with citations."""

        messages = [{"role": "user", "parts": [system_prompt + "\n\n" + user_prompt]}]

        start = time.time()
        response = self.pro_model.generate_content(messages)
        latency = (time.time() - start) * 1000
        logger.info("Chat response generated in %.0fms", latency)

        return response.text

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
    def generate_summary(self, reviews_text: List[str], college_name: str, program_name: str = None) -> dict:
        """Generate a summary of multiple reviews."""
        combined = "\n---\n".join(reviews_text[:20])
        context = f"{college_name}" + (f" - {program_name}" if program_name else "")

        prompt = f"""Summarize these student reviews for {context}.
Return a JSON object with:
- "summary": A 2-3 paragraph balanced summary
- "strengths": Array of top 5 strengths mentioned
- "weaknesses": Array of top 5 weaknesses mentioned

Reviews:
{combined[:5000]}

Respond with ONLY the JSON, no markdown."""

        response = self.pro_model.generate_content(prompt)
        try:
            text = response.text.strip()
            if text.startswith("```"):
                text = text.split("\n", 1)[1].rsplit("```", 1)[0].strip()
            return json.loads(text)
        except (json.JSONDecodeError, ValueError):
            return {
                "summary": response.text,
                "strengths": [],
                "weaknesses": [],
            }

    def generate_embedding(self, text: str) -> List[float]:
        """Generate embedding vector for text."""
        result = genai.embed_content(
            model=f"models/{settings.GEMINI_EMBEDDING_MODEL}",
            content=text,
            task_type="retrieval_document",
        )
        return result["embedding"]

    def generate_query_embedding(self, query: str) -> List[float]:
        """Generate embedding vector for a search query."""
        result = genai.embed_content(
            model=f"models/{settings.GEMINI_EMBEDDING_MODEL}",
            content=query,
            task_type="retrieval_query",
        )
        return result["embedding"]
