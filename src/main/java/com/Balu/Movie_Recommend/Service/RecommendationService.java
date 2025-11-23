package com.Balu.Movie_Recommend.Service;

import com.Balu.Movie_Recommend.Entity.ChatMessage;
import com.Balu.Movie_Recommend.Entity.MovieRecommendation;
import com.Balu.Movie_Recommend.Entity.SenderType;
import com.Balu.Movie_Recommend.Repositories.ChatMessageRepository;
import com.Balu.Movie_Recommend.Repositories.MovieRecommendationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final GeminiClientService geminiClientService;
    private final ChatMessageRepository chatRepo;
    private final MovieRecommendationRepository movieRepo;

    /**
     * Main entry for getting movie suggestions for a user.
     */
    public List<MovieRecommendation> getMovieSuggestions(
            Long userId,
            String userMessage,
            String genre,
            Integer yearFrom,
            Integer yearTo,
            String mood
    ) {
        // 1. Load last N messages for personalization context
        List<ChatMessage> history = chatRepo.findTop20ByUserIdOrderByTimestampDesc(userId);
        String historySummary = buildHistorySummary(history);

        // 2. Build prompt for Gemini
        String prompt = buildPrompt(historySummary, userMessage, genre, yearFrom, yearTo, mood);
        log.info("Sending prompt to Gemini for user {}: {}", userId, prompt);

        // 3. Call Gemini synchronously (GeminiClientService returns raw String)
        String geminiResponse = geminiClientService.getRecommendationsFromGemini(prompt);
        log.info("Gemini raw response for user {}: {}", userId, geminiResponse);

        // 4. Parse movies from Gemini response
        List<MovieRecommendation> movies = parseMoviesFromJson(geminiResponse, userId);
        log.info("Parsed {} movie recommendations for user {}", movies.size(), userId);

        // 5. Persist chat history
        chatRepo.save(ChatMessage.builder()
                .userId(userId)
                .sender(SenderType.USER)
                .content(userMessage)
                .timestamp(LocalDateTime.now())
                .build());

        chatRepo.save(ChatMessage.builder()
                .userId(userId)
                .sender(SenderType.AI)
                .content("Recommended " + movies.size() + " movies.")
                .timestamp(LocalDateTime.now())
                .build());

        // 6. Persist recommendations
        if (!movies.isEmpty()) {
            movieRepo.saveAll(movies);
        }

        return movies;
    }

    /**
     * Converts the last messages into a simple text summary for the prompt.
     */
    private String buildHistorySummary(List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("User's recent movie taste:\n");

        history.stream()
                .sorted(Comparator.comparing(ChatMessage::getTimestamp))
                .forEach(msg -> sb.append(msg.getSender())
                        .append(": ")
                        .append(msg.getContent())
                        .append("\n"));

        return sb.toString();
    }

    /**
     * Builds a strict prompt for Gemini so it returns JSON.
     */
    private String buildPrompt(String history, String userMessage, String genre,
                               Integer yearFrom, Integer yearTo, String mood) {

        return """
        You are a movie recommendation system. ALWAYS return **verified real movies** 
        with valid poster URLs and short trailer preview video URLs.

        --- RULES ---
        1. ALWAYS respond ONLY with a JSON ARRAY.
        2. NO explanation before or after JSON.
        3. Each object MUST include:
           - title (real movie)
           - year
           - genre
           - moodTag (based on user's mood)
           - posterUrl (MUST be a DIRECT IMAGE URL: jpg or png)
           - previewUrl (MUST be a DIRECT VIDEO URL: mp4 or webm)
           - rating (0–10)
        4. Preview videos MUST be SHORT (3–10 seconds), trailer clips, or teaser scenes.
           If real clip not available, use a YouTube trailer turned into a 
           "https://www.yt-download.ai/api/button/mp4/{VIDEO_ID}" link.

        --- CONTEXT ---
        User taste history:
        %s

        Current user request:
        %s

        --- FILTERS ---
        Genre: %s
        Year range: %s to %s
        Mood: %s

        --- OUTPUT FORMAT ---
        Example format:
        [
          {
            "title": "Interstellar",
            "year": "2014",
            "genre": "Sci-Fi, Adventure",
            "moodTag": "inspiring",
            "posterUrl": "https://image.tmdb.org/t/p/w500/rAiYTfKGqDCRIIqo664sY9XZIvQ.jpg",
            "previewUrl": "https://cdn2.unrealengine.com/interstellar-loop.mp4",
            "rating": 8.6
          }
        ]

        Now generate 5–8 high-quality movie recommendations.
        """.formatted(
                history,
                userMessage,
                genre != null ? genre : "any",
                yearFrom != null ? yearFrom : "any",
                yearTo != null ? yearTo : "any",
                mood != null ? mood : "any"
        );
    }


    /**
     * Robustly parses Gemini's response into a list of MovieRecommendation.
     * Handles:
     *  - Pure JSON arrays
     *  - Arrays wrapped in markdown ```json fences
     *  - Extra text before/after the array
     */
    private List<MovieRecommendation> parseMoviesFromJson(String jsonText, Long userId) {
        if (jsonText == null || jsonText.isBlank()) {
            log.warn("Gemini returned empty/blank response");
            return List.of();
        }

        String cleaned = jsonText.trim();

        // 1) Strip markdown fences ``` or ```json ... ```
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            int lastFence = cleaned.lastIndexOf("```");
            if (lastFence != -1) {
                cleaned = cleaned.substring(0, lastFence);
            }
            cleaned = cleaned.trim();
        }

        // 2) Take only the array part (between first '[' and last ']')
        int firstBracket = cleaned.indexOf('[');
        int lastBracket = cleaned.lastIndexOf(']');
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            cleaned = cleaned.substring(firstBracket, lastBracket + 1);
        }

        ObjectMapper mapper = new ObjectMapper();
        List<MovieRecommendation> result = new ArrayList<>();

        try {
            JsonNode root = mapper.readTree(cleaned);

            // Case 1: raw array: [ { ... }, ... ]
            if (root.isArray()) {
                root.forEach(node -> result.add(buildMovieFromNode(node, userId)));
            }
            // Case 2: wrapped object: { "movies": [ ... ] }
            else if (root.has("movies") && root.get("movies").isArray()) {
                root.get("movies").forEach(node -> result.add(buildMovieFromNode(node, userId)));
            } else {
                log.warn("Unexpected JSON structure from Gemini: {}", cleaned);
            }
        } catch (Exception e) {
            log.error("Failed to parse Gemini JSON: {}", cleaned, e);
        }

        return result;
    }

    /**
     * Helper to convert a single JSON node into a MovieRecommendation entity.
     */
    private MovieRecommendation buildMovieFromNode(JsonNode node, Long userId) {
        return MovieRecommendation.builder()
                .userId(userId)
                .title(node.path("title").asText(""))
                .year(node.path("year").asText(""))
                .genre(node.path("genre").asText(""))
                .moodTag(node.path("moodTag").asText(""))
                .posterUrl(node.path("posterUrl").asText(""))
                .rating(node.path("rating").asDouble(0.0))
                .createdAt(LocalDateTime.now())
                .build();
    }
}


