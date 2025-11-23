package com.Balu.Movie_Recommend.DTO;

import lombok.Data;

@Data
public class RecommendationRequest {
    private String userExternalId;
    private String message;
    private String genre;
    private Integer yearFrom;
    private Integer yearTo;
    private String mood;
}

