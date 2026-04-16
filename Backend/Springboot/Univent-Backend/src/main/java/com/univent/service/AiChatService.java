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

    @Value("${ai.worker.base-url:http://localhost:8000}")
    private String aiWorkerBaseUrl;

    public AiChatResponse chat(AiChatRequest request) {
        return post("/api/v1/ai/chat", request, AiChatResponse.class);
    }

    public AiSummarizeResponse summarize(AiSummarizeRequest request) {
        return post("/api/v1/ai/summarize", request, AiSummarizeResponse.class);
    }

    public AiSuggestResponse suggest(AiSuggestRequest request) {
        return post("/api/v1/ai/suggest", request, AiSuggestResponse.class);
    }

    public AiStatsResponse stats() {
        try {
            ResponseEntity<AiStatsResponse> response = restTemplate.getForEntity(
                    aiWorkerBaseUrl + "/api/v1/ai/stats", AiStatsResponse.class);
            return response.getBody();
        } catch (RestClientException ex) {
            log.error("Failed to fetch AI worker stats", ex);
            throw new RuntimeException("AI worker is unavailable");
        }
    }

    private <T> T post(String path, Object payload, Class<T> responseType) {
        try {
            ResponseEntity<T> response = restTemplate.postForEntity(
                    aiWorkerBaseUrl + path, payload, responseType);
            return response.getBody();
        } catch (RestClientException ex) {
            log.error("AI worker request failed for path {}", path, ex);
            throw new RuntimeException("AI worker is unavailable");
        }
    }
}
