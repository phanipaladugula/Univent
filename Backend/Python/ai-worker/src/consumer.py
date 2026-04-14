"""Kafka consumer for processing review events from Spring Boot."""
import json
import logging
import threading
import time
from typing import Callable

from confluent_kafka import Consumer, KafkaError, KafkaException

from src.config import settings
from src.models.schemas import ReviewSubmittedEvent, ReviewProcessedEvent, ModerationResult
from src.services.gemini_service import GeminiService
from src.services.rag_service import RAGService

logger = logging.getLogger(__name__)

TOPIC_REVIEW_SUBMITTED = "review.submitted"
TOPIC_REVIEW_PROCESSED = "review.processed"


class ReviewConsumer:
    """Consumes review.submitted events, processes with AI, and produces review.processed."""

    def __init__(self, gemini: GeminiService, rag: RAGService):
        self.gemini = gemini
        self.rag = rag
        self._running = False
        self._thread = None

        self.consumer_config = {
            "bootstrap.servers": settings.KAFKA_BROKERS,
            "group.id": settings.KAFKA_GROUP_ID,
            "auto.offset.reset": "latest",
            "enable.auto.commit": False,
            "max.poll.interval.ms": 300000,  # 5 min for slow AI processing
        }

        self.producer = None  # Lazy init

    def start(self):
        """Start consuming in a background thread."""
        self._running = True
        self._thread = threading.Thread(target=self._consume_loop, daemon=True)
        self._thread.start()
        logger.info("📡 Kafka consumer started for %s", TOPIC_REVIEW_SUBMITTED)

    def stop(self):
        """Stop the consumer."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=10)
        logger.info("📡 Kafka consumer stopped")

    def _consume_loop(self):
        consumer = Consumer(self.consumer_config)
        consumer.subscribe([TOPIC_REVIEW_SUBMITTED])

        # Lazy init producer
        from confluent_kafka import Producer
        self.producer = Producer({"bootstrap.servers": settings.KAFKA_BROKERS})

        try:
            while self._running:
                msg = consumer.poll(timeout=1.0)

                if msg is None:
                    continue

                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        continue
                    logger.error("Kafka error: %s", msg.error())
                    continue

                try:
                    self._process_message(msg)
                    consumer.commit(message=msg)
                except Exception as e:
                    logger.error("❌ Failed to process review: %s", e, exc_info=True)

        except KafkaException as e:
            logger.error("Kafka exception: %s", e)
        finally:
            consumer.close()

    def _process_message(self, msg):
        """Process a single review.submitted message."""
        start_time = time.time()

        data = json.loads(msg.value().decode("utf-8"))
        event = ReviewSubmittedEvent(**data)

        logger.info("📝 Processing review %s", event.review_id)

        # Step 1: Sentiment Analysis
        sentiment, sentiment_score = self.gemini.analyze_sentiment(
            event.review_text, event.pros, event.cons
        )

        # Step 2: Topic Extraction
        topics = self.gemini.extract_topics(event.review_text)

        # Step 3: Moderation Check
        moderation = self.gemini.check_moderation(event.review_text)

        # Step 4: RAG Indexing (only if content is safe)
        qdrant_indexed = False
        if moderation.get("safe", True):
            try:
                chunks_indexed = self.rag.index_review(
                    review_id=event.review_id,
                    college_id=event.college_id,
                    program_id=event.program_id,
                    review_text=event.review_text,
                    sentiment=sentiment,
                    rating=event.overall_rating or 3,
                    is_verified=event.is_verified,
                    graduation_year=event.graduation_year,
                )
                qdrant_indexed = chunks_indexed > 0
            except Exception as e:
                logger.warning("⚠️ RAG indexing failed for review %s: %s", event.review_id, e)

        processing_time_ms = int((time.time() - start_time) * 1000)

        # Produce review.processed event
        processed = ReviewProcessedEvent(
            review_id=event.review_id,
            sentiment=sentiment,
            sentiment_score=sentiment_score,
            extracted_topics=topics,
            moderation_result=ModerationResult(**moderation),
            qdrant_indexed=qdrant_indexed,
            processing_time_ms=processing_time_ms,
        )

        self.producer.produce(
            TOPIC_REVIEW_PROCESSED,
            key=event.review_id.encode(),
            value=processed.model_dump_json().encode(),
        )
        self.producer.flush()

        logger.info("✅ Review %s processed in %dms (sentiment=%s, topics=%d, safe=%s, indexed=%s)",
                     event.review_id, processing_time_ms, sentiment, len(topics),
                     moderation.get("safe"), qdrant_indexed)
