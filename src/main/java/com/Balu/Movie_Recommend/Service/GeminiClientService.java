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
    private final String apiKeyOrToken;
    private final String baseUrl;
    private final String authType; // "apikey" or "bearer"

    public GeminiClientService(
            @Value("${gemini.api.base-url}") String baseUrl,
            @Value("${gemini.model}") String model,
            @Value("${gemini.api.key}") String apiKeyOrToken,
            @Value("${gemini.auth.type:apikey}") String authType // optional, defaults to apikey
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
        this.model = model;
        this.apiKeyOrToken = apiKeyOrToken;
        this.authType = authType == null ? "apikey" : authType.toLowerCase();

        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Try call with v1beta first, fallback to v1 if 404.
     * Supports two auth modes:
     * - apikey (append ?key=)
     * - bearer (use Authorization header)
     */
    public String getRecommendationsFromGemini(String prompt) {
        String[] versions = new String[] { "v1beta", "v1" };

        Exception lastException = null;

        for (String ver : versions) {
            String url = buildUrlForVersion(ver);

            try {
                String body = buildRequestJson(prompt);
                log.info("Calling Gemini at URL [{}] with authMode=[{}]. Body: {}", url, authType, body);

                WebClient.RequestBodySpec req = webClient.post()
                        .uri(url);

                if ("bearer".equals(authType)) {
                    req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyOrToken);
                }

                // if using API key, we included it in the URL already
                String response = req
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.debug("Gemini raw response: {}", response);

                if (response == null || response.isBlank()) {
                    throw new RuntimeException("Empty response from Gemini at " + url);
                }

                // typical structure: { candidates: [ { content: { parts: [ { text: "..." } ] } } ] }
                JsonNode root = mapper.readTree(response);

                // Try common paths
                String text = root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text")
                        .asText(null);

                if (text == null) {
                    // older responses sometimes place content differently; try alternative
                    text = root.path("candidates")
                            .path(0)
                            .path("content")
                            .path(0)
                            .path("text")
                            .asText(null);
                }

                if (text == null) {
                    // not the expected format — return raw for debugging
                    log.warn("Gemini returned unexpected JSON shape at {}: {}", url, response);
                    return response;
                }

                return text;

            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                String respBody = e.getResponseBodyAsString();
                log.warn("Gemini returned HTTP {} for URL {} (body: {})", status, url, respBody);

                lastException = e;

                // If 404, try next version; otherwise rethrow as runtime
                if (status == 404) {
                    log.info("404 on {}, trying next API version if available...", url);
                    continue;
                } else {
                    throw new RuntimeException("Gemini API error (" + status + "): " + respBody, e);
                }
            } catch (Exception ex) {
                log.error("Error calling Gemini at {}: {}", url, ex.getMessage(), ex);
                lastException = ex;
                // try next version only for 404s; for others, stop
                break;
            }
        }

        // If we reach here, everything failed
        throw new RuntimeException("All Gemini endpoints failed. Last error: " + (lastException == null ? "unknown" : lastException.getMessage()), lastException);
    }

    private String buildUrlForVersion(String version) {
        // version like "v1beta" or "v1"
        // if auth type is API key, append ?key=
        if ("apikey".equals(authType)) {
            return String.format("%s/%s/models/%s:generateContent?key=%s",
                    baseUrl, version, model, apiKeyOrToken);
        } else {
            return String.format("%s/%s/models/%s:generateContent",
                    baseUrl, version, model);
        }
    }

    private String buildRequestJson(String prompt) throws Exception {
        ObjectNode textNode = mapper.createObjectNode();
        textNode.put("text", prompt);

        ObjectNode contentNode = mapper.createObjectNode();
        contentNode.set("parts", mapper.createArrayNode().add(textNode));

        ObjectNode requestNode = mapper.createObjectNode();
        requestNode.set("contents", mapper.createArrayNode().add(contentNode));

        return mapper.writeValueAsString(requestNode);
    }
}




