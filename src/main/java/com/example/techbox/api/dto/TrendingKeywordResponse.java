package com.example.techbox.api.dto;

import com.example.techbox.domain.TrendingKeyword;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class TrendingKeywordResponse {

    private UUID id;
    private String keyword;
    private int count;
    private int prevCount;
    private Double growthRate;
    private LocalDate date;

    public static TrendingKeywordResponse from(TrendingKeyword keyword) {
        return TrendingKeywordResponse.builder()
                .id(keyword.getId())
                .keyword(keyword.getKeyword())
                .count(keyword.getCount())
                .prevCount(keyword.getPrevCount())
                .growthRate(keyword.getGrowthRate())
                .date(keyword.getDate())
                .build();
    }
}
