package com.example.techbox.api.dto;

import com.example.techbox.domain.BatchLog;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class BatchLogResponse {

    private UUID id;
    private String batchType;
    private String status;
    private int fetchedCount;
    private int skippedCount;
    private int errorCount;
    private String errorDetail;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    public static BatchLogResponse from(BatchLog log) {
        return BatchLogResponse.builder()
                .id(log.getId())
                .batchType(log.getBatchType())
                .status(log.getStatus())
                .fetchedCount(log.getFetchedCount())
                .skippedCount(log.getSkippedCount())
                .errorCount(log.getErrorCount())
                .errorDetail(log.getErrorDetail())
                .startedAt(log.getStartedAt())
                .finishedAt(log.getFinishedAt())
                .build();
    }
}
