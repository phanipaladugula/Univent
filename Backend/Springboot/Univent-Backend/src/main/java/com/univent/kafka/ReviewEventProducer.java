package com.univent.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Publishes a review.submitted event when a new review is created.
     * The Python AI worker consumes this to run sentiment analysis,
     * topic extraction, moderation, and RAG indexing.
     */
    public void publishReviewSubmitted(UUID reviewId, UUID collegeId, UUID programId,
                                        String reviewText, String[] pros, String[] cons,
                                        UUID userId, boolean isVerified,
                                        Integer graduationYear, Integer overallRating) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("review_id", reviewId.toString());
            event.put("college_id", collegeId.toString());
            event.put("program_id", programId.toString());
            event.put("review_text", reviewText);
            event.put("pros", Arrays.asList(pros != null ? pros : new String[]{}));
            event.put("cons", Arrays.asList(cons != null ? cons : new String[]{}));
            event.put("user_id", userId.toString());
            event.put("is_verified", isVerified);
            event.put("graduation_year", graduationYear);
            event.put("overall_rating", overallRating);
            event.put("timestamp", LocalDateTime.now().toString());

            String json = mapper.writeValueAsString(event);
            kafkaTemplate.send("review.submitted", reviewId.toString(), json);

            log.info("📤 Published review.submitted for review {}", reviewId);
        } catch (Exception e) {
            log.error("❌ Failed to publish review.submitted: {}", e.getMessage());
        }
    }
}
