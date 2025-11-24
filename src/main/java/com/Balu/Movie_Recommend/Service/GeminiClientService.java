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


    public GeminiClientService(
            @Value("${gemini.api.base-url}") String baseUrl,
            @Value("${gemini.model}") String model,
            @Value("${gemini.api.key}") String apiKey
    ) {
        this.model = model;
        this.apiKey = apiKey;


        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
// use x-goog-api-key for API key transport (Gemini often accepts this header)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }


    public String getRecommendationsFromGemini(String prompt) {
        try {
            String url = String.format("/v1beta/models/%s:generateContent", model);


            ObjectNode textNode = mapper.createObjectNode();
            textNode.put("text", prompt);


            ObjectNode contentNode = mapper.createObjectNode();
            contentNode.set("parts", mapper.createArrayNode().add(textNode));


            ObjectNode requestNode = mapper.createObjectNode();
            requestNode.set("contents", mapper.createArrayNode().add(contentNode));


            String requestJson = mapper.writeValueAsString(requestNode);
            log.info("Sending Gemini request to {}: {}", url, requestJson);


            String rawResponse = webClient.post()
                    .uri(url)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();


            log.debug("Gemini raw response: {}", rawResponse);


            return rawResponse == null ? "" : rawResponse;


        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Error calling Gemini API: " + e.getMessage(), e);
        }
    }
}



