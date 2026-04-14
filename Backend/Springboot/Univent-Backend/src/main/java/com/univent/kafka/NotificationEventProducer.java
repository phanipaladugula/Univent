package com.univent.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());


    public void publishNotification(UUID userId, String type, String title,
                                     String body, Map<String, String> data) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("user_id", userId.toString());
            event.put("type", type);
            event.put("title", title);
            event.put("body", body);
            event.put("data", data != null ? data : Map.of());
            event.put("timestamp", LocalDateTime.now().toString());

            String json = mapper.writeValueAsString(event);
            kafkaTemplate.send("notification.outbound", userId.toString(), json);

            log.debug("📤 Published notification.outbound [{}] for user {}", type, userId);
        } catch (Exception e) {
            log.error("❌ Failed to publish notification: {}", e.getMessage());
        }
    }
}
