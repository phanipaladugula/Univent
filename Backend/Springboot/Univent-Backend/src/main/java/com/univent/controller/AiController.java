package com.univent.controller;

import com.univent.model.dto.request.AiChatRequest;
import com.univent.model.dto.request.AiSuggestRequest;
import com.univent.model.dto.request.AiSummarizeRequest;
import com.univent.model.dto.response.AiChatResponse;
import com.univent.model.dto.response.AiStatsResponse;
import com.univent.model.dto.response.AiSuggestResponse;
import com.univent.model.dto.response.AiSummarizeResponse;
import com.univent.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @RequestHeader(value="X-Test-Delay", required=false) String testDelay) {
        return ResponseEntity.ok(aiChatService.chat(request, testDelay));
    }

    @PostMapping("/summarize")
    public ResponseEntity<AiSummarizeResponse> summarize(@Valid @RequestBody AiSummarizeRequest request) {
        return ResponseEntity.ok(aiChatService.summarize(request));
    }

    @PostMapping("/suggest")
    public ResponseEntity<AiSuggestResponse> suggest(@Valid @RequestBody AiSuggestRequest request) {
        return ResponseEntity.ok(aiChatService.suggest(request));
    }

    @GetMapping("/stats")
    public ResponseEntity<AiStatsResponse> stats() {
        return ResponseEntity.ok(aiChatService.stats());
    }
}
