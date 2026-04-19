package com.smartlife.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Central service for all Llama 3.2 AI interactions via the local Ollama server.
 *
 * All methods degrade gracefully — if Ollama is unavailable, they return
 * null / empty strings so callers can fall back to rule-based logic.
 *
 * Endpoints used:
 *   POST /api/generate  — single-shot text generation
 *   POST /api/chat      — multi-turn conversation (for RAG chatbot)
 */
@Service
@Slf4j
public class OllamaService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String model;

    // Timeout for generation requests (Llama on CPU can be slow)
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    public OllamaService(
            @Value("${smartlife.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${smartlife.ollama.model:llama3.2:3b}") String model) {
        this.baseUrl = baseUrl;
        this.model   = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("OllamaService initialised — model={} endpoint={}", model, baseUrl);
    }

    // ─────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Single-shot text generation.
     * @param systemPrompt  instructions / context (may be null)
     * @param userPrompt    the actual question / task
     * @return AI response text, or null if Ollama is unavailable
     */
    public String generate(String systemPrompt, String userPrompt) {
        try {
            String fullPrompt = systemPrompt != null
                    ? "System: " + systemPrompt + "\n\nUser: " + userPrompt
                    : userPrompt;

            Map<String, Object> body = Map.of(
                    "model",  model,
                    "prompt", fullPrompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.3,
                            "num_predict", 512
                    )
            );

            String json  = MAPPER.writeValueAsString(body);
            String responseJson = post("/api/generate", json);

            GenerateResponse resp = MAPPER.readValue(responseJson, GenerateResponse.class);
            return resp.response() != null ? resp.response().trim() : null;

        } catch (Exception e) {
            log.warn("OllamaService.generate failed (Ollama may be offline): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Multi-turn chat — used by the RAG chatbot.
     * @param messages  ordered list of {role, content} maps
     * @return assistant reply text, or null if unavailable
     */
    public String chat(List<ChatMessage> messages) {
        try {
            Map<String, Object> body = Map.of(
                    "model",    model,
                    "messages", messages,
                    "stream",   false,
                    "options",  Map.of(
                            "temperature", 0.5,
                            "num_predict", 1024
                    )
            );

            String json = MAPPER.writeValueAsString(body);
            String responseJson = post("/api/chat", json);

            ChatResponse resp = MAPPER.readValue(responseJson, ChatResponse.class);
            return resp.message() != null ? resp.message().content().trim() : null;

        } catch (Exception e) {
            log.warn("OllamaService.chat failed (Ollama may be offline): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Quick health check — returns true if Ollama is reachable and the model is loaded.
     */
    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 && resp.body().contains("llama");
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Convenience factory methods for building prompts
    // ─────────────────────────────────────────────────────────────

    public static ChatMessage systemMessage(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage userMessage(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistantMessage(String content) {
        return new ChatMessage("assistant", content);
    }

    // ─────────────────────────────────────────────────────────────
    //  Internal HTTP
    // ─────────────────────────────────────────────────────────────

    private String post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama returned HTTP " + response.statusCode()
                    + ": " + response.body());
        }
        return response.body();
    }

    // ─────────────────────────────────────────────────────────────
    //  DTOs
    // ─────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateResponse(String response, boolean done) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(ChatMessage message, boolean done) {}

    public record ChatMessage(String role, String content) {}
}
