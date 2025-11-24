package com.Balu.Movie_Recommend.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class GeminiClientService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String model;
    private final String apiKey;
    private final String baseUrl;

    public GeminiClientService(
            @Value("${gemini.api.base-url}") String baseUrl,
            @Value("${gemini.model}") String model,
            @Value("${gemini.api.key}") String apiKey
    ) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.webClient = WebClient.builder().build();
    }

    public String getRecommendationsFromGemini(String prompt) {
        try {
            String url = String.format("%s/models/%s:generateContent?key=%s",
                    baseUrl, model, apiKey);

            // ---- Correct JSON structure ----
            ObjectNode textNode = mapper.createObjectNode();
            textNode.put("text", prompt);

            ObjectNode contentNode = mapper.createObjectNode();
            contentNode.put("role", "user");
            contentNode.set("parts", mapper.createArrayNode().add(textNode));

            ObjectNode requestNode = mapper.createObjectNode();
            requestNode.set("contents", mapper.createArrayNode().add(contentNode));

            String requestJson = mapper.writeValueAsString(requestNode);

            log.info("Sending JSON to Gemini: {}", requestJson);

            String rawResponse = webClient.post()
                    .uri(url)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Gemini raw response: {}", rawResponse);

            if (rawResponse == null || rawResponse.isBlank()) {
                return "";
            }

            JsonNode root = mapper.readTree(rawResponse);

            // Extract text
            return root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Error calling Gemini API: " + e.getMessage(), e);
        }
    }
}




