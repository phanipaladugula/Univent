package com.univent.service;

import com.univent.config.RequestCorrelationFilter;
import com.univent.config.SecretProvider;
import com.univent.model.dto.request.AiChatRequest;
import com.univent.model.dto.request.AiSuggestRequest;
import com.univent.model.dto.request.AiSummarizeRequest;
import com.univent.model.dto.response.AiChatResponse;
import com.univent.model.dto.response.AiStatsResponse;
import com.univent.model.dto.response.AiSuggestResponse;
import com.univent.model.dto.response.AiSummarizeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private final RestTemplate restTemplate;
    private final SecretProvider secretProvider;

    @Value("${ai.worker.url:http://localhost:8000}")
    private String aiWorkerBaseUrl;

    private HttpHeaders getHeaders(String testDelay) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", secretProvider.getInternalSharedSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
        if (requestId != null && !requestId.isBlank()) {
            headers.set(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId);
        }

        if (testDelay != null && !testDelay.isBlank()) {
            headers.set("X-Test-Delay", testDelay);
        }
        return headers;
    }

    public AiChatResponse chat(AiChatRequest request, String testDelay) {
        return post("/api/v1/ai/chat", request, AiChatResponse.class, testDelay);
    }

    public AiSummarizeResponse summarize(AiSummarizeRequest request) {
        return post("/api/v1/ai/summarize", request, AiSummarizeResponse.class, null);
    }

    public AiSuggestResponse suggest(AiSuggestRequest request) {
        return post("/api/v1/ai/suggest", request, AiSuggestResponse.class, null);
    }

    public AiStatsResponse stats() {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(getHeaders(null));
            ResponseEntity<AiStatsResponse> response = restTemplate.exchange(
                    aiWorkerBaseUrl + "/api/v1/ai/stats",
                    HttpMethod.GET,
                    entity,
                    AiStatsResponse.class);
            return requireBody(response, "AI stats");
        } catch (RestClientException ex) {
            log.error("Failed to fetch AI worker stats", ex);
            throw new RuntimeException("AI worker is unavailable");
        }
    }

    private <T> T post(String path, Object payload, Class<T> responseType, String testDelay) {
        try {
            HttpEntity<Object> entity = new HttpEntity<>(payload, getHeaders(testDelay));
            ResponseEntity<T> response = restTemplate.exchange(
                    aiWorkerBaseUrl + path,
                    HttpMethod.POST,
                    entity,
                    responseType);
            return requireBody(response, path);
        } catch (RestClientException ex) {
            log.error("AI worker request failed for path {}", path, ex);
            throw new RuntimeException("AI worker is unavailable");
        }
    }

    private <T> T requireBody(ResponseEntity<T> response, String operation) {
        if (!response.hasBody() || response.getBody() == null) {
            throw new RuntimeException("AI worker returned an empty response for " + operation);
        }
        return response.getBody();
    }
}
