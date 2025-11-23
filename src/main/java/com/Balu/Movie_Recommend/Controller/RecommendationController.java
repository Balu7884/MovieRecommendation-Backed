package com.Balu.Movie_Recommend.Controller;

import com.Balu.Movie_Recommend.DTO.RecommendationRequest;
import com.Balu.Movie_Recommend.Entity.AppUser;
import com.Balu.Movie_Recommend.Entity.MovieRecommendation;
import com.Balu.Movie_Recommend.Repositories.AppUserRepository;
import com.Balu.Movie_Recommend.Service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173") // Vite default
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final AppUserRepository userRepo;

    @PostMapping
    public ResponseEntity<List<MovieRecommendation>> recommend(@RequestBody RecommendationRequest request) {
        // Map external userId (from frontend) to DB user:
        AppUser user = userRepo.findByExternalId(request.getUserExternalId())
                .orElseGet(() -> userRepo.save(AppUser.builder()
                        .externalId(request.getUserExternalId())
                        .displayName("Guest")
                        .build()));

        List<MovieRecommendation> movies = recommendationService.getMovieSuggestions(
                user.getId(),
                request.getMessage(),
                request.getGenre(),
                request.getYearFrom(),
                request.getYearTo(),
                request.getMood()
        );

        return ResponseEntity.ok(movies);
    }
}


