package com.Balu.Movie_Recommend.Controller;

import com.Balu.Movie_Recommend.Service.GeminiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class GeminiTestController {

    private final GeminiClientService geminiClientService;

    /**
     * Simple endpoint to test Gemini connectivity.
     * Returns raw Gemini response (no parsing).
     */
    @GetMapping(value = "/gemini", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> testGemini(
            @RequestParam(defaultValue = "Suggest 3 feel-good movies from 2015 onwards.") String prompt
    ) {
        try {
            log.info("Testing Gemini connection with prompt: {}", prompt);

            String result = geminiClientService.getRecommendationsFromGemini(prompt);

            if (result == null || result.isBlank()) {
                log.warn("Gemini returned empty response");
                return ResponseEntity.status(502)
                        .body("{\"error\":\"Empty response from Gemini\"}");
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(result);

        } catch (Exception e) {
            log.error("Error in /api/test/gemini: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body("{\"error\":\"Gemini request failed: " + e.getMessage() + "\"}");
        }
    }
}


