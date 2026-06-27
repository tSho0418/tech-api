package com.example.techbox.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "batch_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_type", nullable = false, length = 20)
    private String batchType;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "fetched_count", nullable = false)
    @Builder.Default
    private int fetchedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    @Builder.Default
    private int skippedCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private int errorCount = 0;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public void complete(String finalStatus, int fetched, int skipped, int errors, String errorDetail) {
        this.status = finalStatus;
        this.fetchedCount = fetched;
        this.skippedCount = skipped;
        this.errorCount = errors;
        this.errorDetail = errorDetail;
        this.finishedAt = OffsetDateTime.now();
    }
}
