package com.Balu.Movie_Recommend.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            String url = "%s/models/%s:generateContent?key=%s"
                    .formatted(baseUrl, model, apiKey);

            // Wrap prompt properly as JSON
            String requestJson = """
                    {
                      "contents": [
                        {
                          "parts": [
                            {
                              "text": %s
                            }
                          ]
                        }
                      ]
                    }
                    """.formatted(mapper.writeValueAsString(prompt)); // escape quotes etc.

            String rawResponse = webClient.post()
                    .uri(url)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)   // 👈 IMPORTANT: read as String, NOT JsonNode
                    .block();

            log.info("Gemini raw response: {}", rawResponse);

            if (rawResponse == null || rawResponse.isBlank()) {
                return "";
            }

            // Now parse with our own ObjectMapper (com.fasterxml)
            JsonNode root = mapper.readTree(rawResponse);

            // Standard Gemini structure:
            // candidates[0].content.parts[0].text
            String text = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");

            log.info("Gemini extracted text: {}", text);
            return text;

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Error calling Gemini API: " + e.getMessage(), e);
        }
    }
}



