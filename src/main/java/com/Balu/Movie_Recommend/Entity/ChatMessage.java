package com.Balu.Movie_Recommend.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;               // <-- JPA primary key

    private Long userId;

    @Enumerated(EnumType.STRING)
    private SenderType sender;     // USER or AI

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime timestamp;
}


