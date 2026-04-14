package com.univent.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.univent.model.entity.Review;
import com.univent.model.enums.ReviewStatus;
import com.univent.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEventConsumer {

    private final ReviewRepository reviewRepository;
    private final NotificationEventProducer notificationProducer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Consumes review.processed events from the Python AI worker.
     * Updates the review entity with AI-generated data (sentiment, topics, moderation).
     * If moderation flags the content, the review status changes to FLAGGED.
     */
    @KafkaListener(topics = "review.processed", groupId = "spring-boot-reviews")
    @Transactional
    public void onReviewProcessed(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String reviewId = event.get("review_id").asText();
            String sentiment = event.get("sentiment").asText();
            double sentimentScore = event.get("sentiment_score").asDouble();

            List<String> topics = new ArrayList<>();
            if (event.has("extracted_topics")) {
                event.get("extracted_topics").forEach(t -> topics.add(t.asText()));
            }

            boolean moderationSafe = true;
            String moderationReason = null;
            if (event.has("moderation_result")) {
                JsonNode modResult = event.get("moderation_result");
                moderationSafe = modResult.get("safe").asBoolean(true);
                if (!moderationSafe && modResult.has("reason")) {
                    moderationReason = modResult.get("reason").asText();
                }
            }

            UUID id = UUID.fromString(reviewId);
            Optional<Review> optReview = reviewRepository.findById(id);

            if (optReview.isEmpty()) {
                log.warn("⚠️ Review {} not found for AI processing", reviewId);
                return;
            }

            Review review = optReview.get();
            review.setSentiment(com.univent.model.enums.SentimentType.valueOf(sentiment.toUpperCase()));
            review.setSentimentScore(java.math.BigDecimal.valueOf(sentimentScore));
            review.setExtractedTopics(topics.toArray(new String[0]));
            review.setIsAiProcessed(true);

            if (!moderationSafe) {
                review.setStatus(ReviewStatus.FLAGGED);
                log.warn("🚫 Review {} flagged by AI moderation: {}", reviewId, moderationReason);
            }

            reviewRepository.save(review);

            // Send notification to the review author
            notificationProducer.publishNotification(
                    review.getUser().getId(),
                    "AI_PROCESSING_COMPLETE",
                    "Review Processed",
                    "Your review has been analyzed. Sentiment: " + sentiment,
                    Map.of("review_id", reviewId, "sentiment", sentiment)
            );

            log.info("✅ Review {} updated with AI data (sentiment={}, topics={})",
                    reviewId, sentiment, topics.size());

        } catch (Exception e) {
            log.error("❌ Failed to process review.processed event: {}", e.getMessage(), e);
        }
    }
}
