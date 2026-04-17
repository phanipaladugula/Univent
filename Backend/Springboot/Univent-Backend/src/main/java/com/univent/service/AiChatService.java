package com.univent.service;

import com.univent.model.dto.request.AiChatRequest;
import com.univent.model.dto.request.AiSuggestRequest;
import com.univent.model.dto.request.AiSummarizeRequest;
import com.univent.model.dto.response.AiChatResponse;
import com.univent.model.dto.response.AiStatsResponse;
import com.univent.model.dto.response.AiSuggestResponse;
import com.univent.model.dto.response.AiSummarizeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private org.springframework.http.HttpHeaders getHeaders(String testDelay) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", secretProvider.getInternalSharedSecret());
        if (testDelay != null && !testDelay.isEmpty()) {
            headers.set("X-Test-Delay", testDelay);
        }
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
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
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(getHeaders(null));
            ResponseEntity<AiStatsResponse> response = restTemplate.exchange(
                    aiWorkerBaseUrl + "/api/v1/ai/stats",
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    AiStatsResponse.class);
            return response.getBody();
        } catch (RestClientException ex) {
            log.error("Failed to fetch AI worker stats", ex);
            throw new RuntimeException("AI worker is unavailable");
        }
    }

    private <T> T post(String path, Object payload, Class<T> responseType, String testDelay) {
        try {
            org.springframework.http.HttpEntity<Object> entity = new org.springframework.http.HttpEntity<>(payload, getHeaders(testDelay));
            ResponseEntity<T> response = restTemplate.exchange(
                    aiWorkerBaseUrl + path,
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    responseType);
            return response.getBody();
        } catch (RestClientException ex) {
            log.error("AI worker request failed for path {}", path, ex);
            throw new RuntimeException("AI worker is unavailable");
        }
    }
}
