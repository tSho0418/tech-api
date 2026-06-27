package com.example.techbox.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "trending_keywords",
    uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "date"})
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendingKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(nullable = false)
    @Builder.Default
    private int count = 0;

    @Column(name = "prev_count", nullable = false)
    @Builder.Default
    private int prevCount = 0;

    @Column(name = "growth_rate")
    private Double growthRate;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_ids", columnDefinition = "uuid[]")
    private UUID[] sourceIds;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public void updateCount(int newCount) {
        this.prevCount = this.count;
        this.count = newCount;
        this.growthRate = prevCount == 0 ? null : (double)(newCount - prevCount) / prevCount;
    }
}
