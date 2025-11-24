package com.Balu.Movie_Recommend.Service;

import com.Balu.Movie_Recommend.Entity.AppUser;
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

    // Regex to extract any JSON array
    private static final Pattern ARRAY_PATTERN =
            Pattern.compile("\\[(?:[^\\]\\[]|\\n|\\r)*?\\]", Pattern.DOTALL);

    public List<MovieRecommendation> getMovieSuggestions(
            Long userId,
            String userMessage,
            String genre,
            Integer yearFrom,
            Integer yearTo,
            String mood
    ) {

        // Wrap userId inside an AppUser for relational mapping
        AppUser userRef = AppUser.builder().id(userId).build();

        // Fetch chat history
        List<ChatMessage> history = chatRepo.findTop20ByUserIdOrderByTimestampDesc(userId);
        String historySummary = buildHistorySummary(history);

        // Build prompt
        String prompt = buildPrompt(historySummary, userMessage, genre, yearFrom, yearTo, mood);
        log.info("Sending prompt to Gemini for user {}", userId);

        // Call Gemini
        String geminiResponse = geminiClientService.getRecommendationsFromGemini(prompt);
        log.debug("Gemini raw response for user {}: {}", userId, geminiResponse);

        // Parse JSON
        List<MovieRecommendation> movies = parseMoviesFromJson(geminiResponse, userId);
        log.info("Parsed {} movies for user {}", movies.size(), userId);

        // Save USER chat message
        chatRepo.save(ChatMessage.builder()
                .user(userRef)               // FIXED (relation)
                .sender(SenderType.USER)
                .content(userMessage)
                .build());

        // Save AI response metadata
        chatRepo.save(ChatMessage.builder()
                .user(userRef)               // FIXED (relation)
                .sender(SenderType.AI)
                .content("Recommended " + movies.size() + " movies.")
                .build());

        // Save movie recommendations
        if (!movies.isEmpty()) {
            movieRepo.saveAll(movies);
        }

        return movies;
    }

    private String buildHistorySummary(List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder("User recent taste:\n");

        history.stream()
                .sorted(Comparator.comparing(ChatMessage::getTimestamp))
                .forEach(msg -> sb.append(msg.getSender())
                        .append(": ")
                        .append(msg.getContent())
                        .append("\n"));

        return sb.toString();
    }

    private String buildPrompt(String history, String userMessage, String genre,
                               Integer yearFrom, Integer yearTo, String mood) {

        return """
                You are a movie recommendation engine.
                Respond ONLY with a JSON ARRAY of movie objects. NO TEXT. NO MARKDOWN.

                Required fields:
                title, year, genre, moodTag, posterUrl, previewUrl, rating

                User history:
                %s

                User request:
                %s

                Filters:
                Genre=%s, Year=%s to %s, Mood=%s

                Generate 5–8 real movies.
                """.formatted(
                history,
                userMessage,
                genre != null ? genre : "any",
                yearFrom != null ? yearFrom : "any",
                yearTo != null ? yearTo : "any",
                mood != null ? mood : "any"
        );
    }

    private List<MovieRecommendation> parseMoviesFromJson(String jsonText, Long userId) {
        if (jsonText == null || jsonText.isBlank()) {
            log.warn("Empty Gemini response");
            return List.of();
        }

        String cleaned = extractJsonArray(jsonText);
        if (cleaned == null) {
            log.warn("No JSON array found in response");
            return List.of();
        }

        ObjectMapper mapper = new ObjectMapper();
        List<MovieRecommendation> list = new ArrayList<>();

        try {
            JsonNode root = mapper.readTree(cleaned);

            if (root.isArray()) {
                root.forEach(node -> list.add(buildMovieFromNode(node, userId)));
            }
        } catch (Exception e) {
            log.error("JSON parse error: {}", cleaned, e);
        }

        return list;
    }

    private String extractJsonArray(String text) {
        Matcher m = ARRAY_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    private MovieRecommendation buildMovieFromNode(JsonNode node, Long userId) {
        return MovieRecommendation.builder()
                .userId(userId)
                .title(node.path("title").asText(""))
                .year(node.path("year").asText(""))
                .genre(node.path("genre").asText(""))
                .moodTag(node.path("moodTag").asText(""))
                .posterUrl(node.path("posterUrl").asText(""))
                .previewUrl(node.path("previewUrl").asText(""))
                .rating(node.path("rating").asDouble(0.0))
                .createdAt(LocalDateTime.now())
                .build();
    }
}




