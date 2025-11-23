package com.Balu.Movie_Recommend.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;           // <-- JPA primary key

    @Column(unique = true)
    private String externalId;

    private String displayName;
}



