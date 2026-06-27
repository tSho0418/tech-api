package com.example.techbox.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SourceStatsResponse {

    private String sourceName;
    private long todayCount;
    private long weekCount;
}
