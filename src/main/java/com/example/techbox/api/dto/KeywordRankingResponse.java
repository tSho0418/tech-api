package com.example.techbox.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KeywordRankingResponse {

    private String keyword;
    private long totalCount;
}
