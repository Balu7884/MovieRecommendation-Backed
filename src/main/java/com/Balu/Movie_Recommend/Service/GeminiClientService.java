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
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String getRecommendationsFromGemini(String prompt) {
        try {
            // Correct URL (no double v1beta issue)
            String url = String.format(
                    "%s/v1beta/models/%s:generateContent?key=%s",
                    baseUrl,
                    model,
                    apiKey
            );

            // Build the correct JSON request
            ObjectNode textNode = mapper.createObjectNode();
            textNode.put("text", prompt);

            ObjectNode contentNode = mapper.createObjectNode();
            contentNode.set("parts", mapper.createArrayNode().add(textNode));

            ObjectNode requestNode = mapper.createObjectNode();
            requestNode.set("contents", mapper.createArrayNode().add(contentNode));

            String requestJson = mapper.writeValueAsString(requestNode);

            log.info("Sending Gemini request to {} => {}", url, requestJson);

            String response = webClient.post()
                    .uri(url)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Gemini response: {}", response);

            if (response == null || response.isBlank()) return "";

            JsonNode root = mapper.readTree(response);

            return root.path("candidates")
                    .path(0)
                    .path("content")
                    .path(0)
                    .path("text")
                    .asText("");

        } catch (WebClientResponseException e) {

            // Spring 7+ compatible error handling
            int status = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();

            log.error("Gemini API error {}: {}", status, body);

            throw new RuntimeException(
                    "Gemini API error (" + status + "): " + body
            );

        } catch (Exception e) {
            log.error("Unknown Gemini error", e);
            throw new RuntimeException("Gemini API failed: " + e.getMessage());
        }
    }
}




