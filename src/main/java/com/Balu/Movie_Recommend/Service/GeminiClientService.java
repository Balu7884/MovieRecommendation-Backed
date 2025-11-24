package com.Balu.Movie_Recommend.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiClientService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url}")
    private String baseUrl;      // MUST be: https://generativelanguage.googleapis.com/v1beta

    @Value("${gemini.model}")
    private String model;        // gemini-2.5-flash

    private final WebClient webClient = WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

    public String getRecommendationsFromGemini(String prompt) {

        // FINAL CORRECT URL (no extra /v1beta/models)
        String endpoint = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;

        log.info("Calling Gemini endpoint: {}", endpoint);

        String json = """
        {
            "contents":[
                {
                    "parts":[
                        {"text":"%s"}
                    ]
                }
            ]
        }
        """.formatted(escapeJson(prompt));

        try {
            return webClient.post()
                    .uri(endpoint)
                    .bodyValue(json)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (WebClientResponseException ex) {
            log.error("Gemini Error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return "{ \"error\": \"Gemini request failed: " + ex.getMessage() + "\" }";
        }
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"");
    }
}




