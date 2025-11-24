package com.Balu.Movie_Recommend.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "app_user",
        indexes = {
                @Index(name = "idx_user_external_id", columnList = "externalId")
        }
)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;          // Internal DB primary key

    @Column(unique = true, nullable = false, length = 200)
    private String externalId;   // Internal mapping for frontend UUID/userKey

    @Column(nullable = false, length = 150)
    private String displayName;  // "Guest" or user-defined name (if extended)

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;   // Automatically generated

    @UpdateTimestamp
    private LocalDateTime updatedAt;   // Auto-updated on change
}



