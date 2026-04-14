package com.univent.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Publishes an audit.events event consumed by the Go edge service
     * for persistent audit logging.
     */
    public void publishAuditEvent(UUID actorId, String actorRole, String action,
                                   String resourceType, UUID resourceId,
                                   Map<String, Object> metadata) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("actor_id", actorId != null ? actorId.toString() : "");
            event.put("actor_role", actorRole != null ? actorRole : "");
            event.put("actor_ip", getClientIP());
            event.put("actor_fingerprint", getFingerprint());
            event.put("action", action);
            event.put("resource_type", resourceType);
            event.put("resource_id", resourceId != null ? resourceId.toString() : "");
            event.put("metadata", metadata != null ? metadata : Map.of());
            event.put("timestamp", LocalDateTime.now().toString());

            String json = mapper.writeValueAsString(event);
            kafkaTemplate.send("audit.events", action, json);

            log.debug("📤 Published audit.events [{}] for {}/{}", action, resourceType, resourceId);
        } catch (Exception e) {
            log.error("❌ Failed to publish audit event: {}", e.getMessage());
        }
    }

    private String getClientIP() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    return xff.split(",")[0].trim();
                }
                String xRI = request.getHeader("X-Real-IP");
                if (xRI != null) return xRI;
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String getFingerprint() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("X-Device-Fingerprint");
            }
        } catch (Exception ignored) {}
        return "";
    }
}
