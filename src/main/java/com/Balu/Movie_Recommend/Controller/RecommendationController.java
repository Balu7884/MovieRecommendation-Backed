package com.Balu.Movie_Recommend.Controller;

import com.Balu.Movie_Recommend.DTO.RecommendationRequest;
import com.Balu.Movie_Recommend.Entity.AppUser;
import com.Balu.Movie_Recommend.Entity.MovieRecommendation;
import com.Balu.Movie_Recommend.Repositories.AppUserRepository;
import com.Balu.Movie_Recommend.Service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "https://your-production-domain.com"
})
@Slf4j
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final AppUserRepository userRepo;

    @PostMapping
    public ResponseEntity<?> recommend(@RequestBody RecommendationRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body("Request body is missing.");
            }

            if (request.getUserExternalId() == null || request.getUserExternalId().isBlank()) {
                return ResponseEntity.badRequest()
                        .body("userExternalId is required.");
            }

            log.info("Received recommendation request from userExternalId={}, filters: genre={}, mood={}",
                    request.getUserExternalId(), request.getGenre(), request.getMood());

            // Map external ID to internal DB user
            AppUser user = userRepo.findByExternalId(request.getUserExternalId())
                    .orElseGet(() -> {
                        log.info("Creating new user for externalId={}", request.getUserExternalId());
                        return userRepo.save(AppUser.builder()
                                .externalId(request.getUserExternalId())
                                .displayName("Guest")
                                .build());
                    });

            // Call service
            List<MovieRecommendation> movies = recommendationService.getMovieSuggestions(
                    user.getId(),
                    request.getMessage(),
                    request.getGenre(),
                    request.getYearFrom(),
                    request.getYearTo(),
                    request.getMood()
            );

            log.info("Returning {} recommendations to user {}", movies.size(), user.getId());

            return ResponseEntity.ok(movies);

        } catch (Exception ex) {
            log.error("Error while generating recommendations", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error while generating recommendations: " + ex.getMessage());
        }
    }
}



