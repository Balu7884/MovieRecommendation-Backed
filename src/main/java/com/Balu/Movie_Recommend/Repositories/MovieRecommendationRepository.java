package com.Balu.Movie_Recommend.Repositories;

import com.Balu.Movie_Recommend.Entity.MovieRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRecommendationRepository extends JpaRepository<MovieRecommendation, Long> {
    List<MovieRecommendation> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
}
