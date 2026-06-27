package com.example.techbox.api.dto;

import com.example.techbox.domain.Summary;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SummaryResponse {

    private UUID id;
    private String summaryJa;
    private String reasonJa;
    private Double qualityScore;
    private String modelUsed;

    public static SummaryResponse from(Summary summary) {
        return SummaryResponse.builder()
                .id(summary.getId())
                .summaryJa(summary.getSummaryJa())
                .reasonJa(summary.getReasonJa())
                .qualityScore(summary.getQualityScore())
                .modelUsed(summary.getModelUsed())
                .build();
    }
}
