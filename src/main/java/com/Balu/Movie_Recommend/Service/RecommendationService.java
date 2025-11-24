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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final GeminiClientService geminiClientService;
    private final ChatMessageRepository chatRepo;
    private final MovieRecommendationRepository movieRepo;

    // Regex to find first JSON array anywhere in text
    private static final Pattern ARRAY_PATTERN =
            Pattern.compile("\\[(?:[^\\]\\[]|\\n|\\r)*?\\]", Pattern.DOTALL);

    /**
     * Main recommendation workflow.
     */
    public List<MovieRecommendation> getMovieSuggestions(
            Long userId,
            String userMessage,
            String genre,
            Integer yearFrom,
            Integer yearTo,
            String mood
    ) {
        // Fetch previous chat messages
        List<ChatMessage> history = chatRepo.findTop20ByUserIdOrderByTimestampDesc(userId);
        String historySummary = buildHistorySummary(history);

        // Build prompt
        String prompt = buildPrompt(historySummary, userMessage, genre, yearFrom, yearTo, mood);
        log.info("Sending prompt to Gemini for user {}", userId);

        // Call Gemini
        String geminiResponse = geminiClientService.getRecommendationsFromGemini(prompt);
        log.debug("Gemini raw response for user {}: {}", userId, geminiResponse);

        // Parse
        List<MovieRecommendation> movies = parseMoviesFromJson(geminiResponse, userId);
        log.info("Parsed {} movie recommendations for user {}", movies.size(), userId);

        // Save chat logs
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

        // Save recommendations
        if (!movies.isEmpty()) {
            movieRepo.saveAll(movies);
        }

        return movies;
    }

    /**
     * Summarizes user's previous conversation messages.
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
     * Builds strict prompt forcing Gemini to output only a JSON array.
     */
    private String buildPrompt(String history, String userMessage,
                               String genre, Integer yearFrom, Integer yearTo, String mood) {

        return """
                You are a movie recommendation engine.
                IMPORTANT: Return ONLY a JSON ARRAY. NO TEXT. NO MARKDOWN. NO EXTRA CONTENT.

                Each movie object MUST include fields:
                - title
                - year
                - genre
                - moodTag
                - posterUrl  (direct JPG/PNG image URL)
            - previewUrl (direct MP4/WebM 3–10 sec clip)
                - rating     (0–10)

                If you must use YouTube trailer, convert into:
                https://www.yt-download.ai/api/button/mp4/{VIDEO_ID}

                --- USER HISTORY ---
                %s

                --- CURRENT REQUEST ---
                %s

                --- FILTERS ---
                Genre: %s
                Year range: %s to %s
                Mood: %s

                Generate 5–8 high-quality verified real movie recommendations.
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
     * Extracts & parses JSON array from Gemini output.
     */
    private List<MovieRecommendation> parseMoviesFromJson(String jsonText, Long userId) {
        if (jsonText == null || jsonText.isBlank()) {
            log.warn("Gemini returned empty response");
            return List.of();
        }

        String cleaned = jsonText.trim();

        // Remove markdown fences
        if (cleaned.startsWith("```")) {
            int lastFence = cleaned.lastIndexOf("```");
            if (lastFence != -1) {
                cleaned = cleaned.substring(0, lastFence)
                        .replaceFirst("^```.*?\\n", "")
                        .trim();
            }
        }

        // Extract JSON array using regex
        String extractedArray = extractJsonArray(cleaned);
        if (extractedArray == null) {
            log.warn("No JSON array found in Gemini response: {}", cleaned);
            return List.of();
        }

        ObjectMapper mapper = new ObjectMapper();
        List<MovieRecommendation> result = new ArrayList<>();

        try {
            JsonNode root = mapper.readTree(extractedArray);

            if (root.isArray()) {
                root.forEach(node -> result.add(buildMovieFromNode(node, userId)));
            } else if (root.has("movies") && root.get("movies").isArray()) {
                root.get("movies").forEach(node -> result.add(buildMovieFromNode(node, userId)));
            } else {
                log.warn("Unexpected JSON structure: {}", extractedArray);
            }

        } catch (Exception e) {
            log.error("Failed to parse Gemini JSON: {}", extractedArray, e);
        }

        return result;
    }

    /**
     * Regex-based extractor for any JSON array found in the string.
     */
    private String extractJsonArray(String text) {
        Matcher m = ARRAY_PATTERN.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    /**
     * Converts JSON node to MovieRecommendation entity.
     */
    private MovieRecommendation buildMovieFromNode(JsonNode node, Long userId) {
        return MovieRecommendation.builder()
                .userId(userId)
                .title(node.path("title").asText(""))
                .year(node.path("year").asText(""))
                .genre(node.path("genre").asText(""))
                .moodTag(node.path("moodTag").asText(""))
                .posterUrl(node.path("posterUrl").asText(""))
                .previewUrl(node.path("previewUrl").asText(""))   // Important
                .rating(node.path("rating").asDouble(0.0))
                .createdAt(LocalDateTime.now())
                .build();
    }
}



