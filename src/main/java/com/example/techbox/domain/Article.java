package com.example.techbox.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "articles")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String url;

    @Column(length = 100)
    private String author;

    private Integer score;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(nullable = false, length = 5)
    @Builder.Default
    private String language = "en";

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "fetched_at", nullable = false)
    @Builder.Default
    private OffsetDateTime fetchedAt = OffsetDateTime.now();

    @Column(name = "is_notified", nullable = false)
    @Builder.Default
    private boolean notified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public void markNotified() {
        this.notified = true;
    }
}
