package com.example.techbox.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "summaries")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Summary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false, unique = true)
    private Article article;

    @Column(name = "summary_ja", nullable = false)
    private String summaryJa;

    @Column(name = "reason_ja")
    private String reasonJa;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "model_used", nullable = false, length = 50)
    private String modelUsed;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
