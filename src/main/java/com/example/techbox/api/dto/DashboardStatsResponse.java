package com.example.techbox.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardStatsResponse {

    private long totalArticles;
    private long todayArticles;
    private long sourceCount;
    private long summaryCount;
}
