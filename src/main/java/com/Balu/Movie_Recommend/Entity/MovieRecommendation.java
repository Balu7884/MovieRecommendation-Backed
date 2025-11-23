package com.Balu.Movie_Recommend.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String title;
    private String year;
    private String genre;
    private String moodTag;
    private String posterUrl;
    private Double rating;
    private LocalDateTime createdAt;
}


