package com.Balu.Movie_Recommend.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Incoming request DTO for movie recommendations.
 * All fields are optional except userExternalId and message.
 */
@Data
public class RecommendationRequest {

    @NotBlank(message = "userExternalId is required")
    private String userExternalId;

    @NotBlank(message = "Message cannot be empty")
    @Size(min = 2, max = 500, message = "Message must be between 2 and 500 characters")
    private String message;

    // Optional filters
    private String genre;            // Example: "Action", "Comedy"
    private Integer yearFrom;        // Example: 2005
    private Integer yearTo;          // Example: 2023
    private String mood;             // Example: "happy", "dark", "romantic"
}


