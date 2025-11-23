package com.Balu.Movie_Recommend.Controller;

import com.Balu.Movie_Recommend.Service.GeminiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class GeminiTestController {

    private final GeminiClientService geminiClientService;

    @GetMapping("/gemini")
    public ResponseEntity<String> testGemini(
            @RequestParam(defaultValue = "Suggest 3 feel-good movies from 2015 onwards.") String prompt
    ) {
        try {
            String result = geminiClientService.getRecommendationsFromGemini(prompt);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in /api/test/gemini", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}


