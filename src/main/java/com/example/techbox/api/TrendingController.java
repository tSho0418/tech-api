package com.example.techbox.api;

import com.example.techbox.api.dto.*;
import com.example.techbox.repository.ArticleRepository;
import com.example.techbox.repository.SourceRepository;
import com.example.techbox.repository.SummaryRepository;
import com.example.techbox.repository.TrendingKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trending")
@RequiredArgsConstructor
public class TrendingController {

    private final TrendingKeywordRepository trendingKeywordRepository;
    private final ArticleRepository articleRepository;
    private final SummaryRepository summaryRepository;
    private final SourceRepository sourceRepository;

    @GetMapping("/keywords")
    public List<KeywordRankingResponse> getTopKeywords(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {

        LocalDate since = LocalDate.now().minusDays(days - 1);

        return trendingKeywordRepository
                .sumCountByKeywordSince(since, PageRequest.of(0, limit))
                .stream()
                .map(row -> KeywordRankingResponse.builder()
                        .keyword((String) row[0])
                        .totalCount(((Number) row[1]).longValue())
                        .build())
                .toList();
    }

    @GetMapping("/keywords/{keyword}/history")
    public List<TrendingKeywordResponse> getKeywordHistory(@PathVariable String keyword) {
        return trendingKeywordRepository.findHistoryByKeyword(keyword).stream()
                .map(TrendingKeywordResponse::from)
                .toList();
    }

    @GetMapping("/sources")
    public List<SourceStatsResponse> getSourceStats() {
        OffsetDateTime todayStart = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime todayEnd = todayStart.plusDays(1);
        OffsetDateTime weekStart = todayStart.minusDays(6);

        Map<String, Long> todayCounts = toCountMap(
                articleRepository.countBySourceForPeriod(todayStart, todayEnd));
        Map<String, Long> weekCounts = toCountMap(
                articleRepository.countBySourceForPeriod(weekStart, todayEnd));

        // 全ソースを網羅（週集計に含まれるものをベースに）
        Set<String> allSources = new LinkedHashSet<>();
        allSources.addAll(weekCounts.keySet());
        allSources.addAll(todayCounts.keySet());

        return allSources.stream()
                .map(name -> SourceStatsResponse.builder()
                        .sourceName(name)
                        .todayCount(todayCounts.getOrDefault(name, 0L))
                        .weekCount(weekCounts.getOrDefault(name, 0L))
                        .build())
                .sorted(Comparator.comparingLong(SourceStatsResponse::getWeekCount).reversed())
                .toList();
    }

    @GetMapping("/stats")
    public DashboardStatsResponse getDashboardStats() {
        OffsetDateTime todayStart = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime todayEnd = todayStart.plusDays(1);

        long totalArticles = articleRepository.count();
        long todayArticles = articleRepository.countByFetchedAtBetween(todayStart, todayEnd);
        long sourceCount = sourceRepository.count();
        long summaryCount = summaryRepository.count();

        return DashboardStatsResponse.builder()
                .totalArticles(totalArticles)
                .todayArticles(todayArticles)
                .sourceCount(sourceCount)
                .summaryCount(summaryCount)
                .build();
    }

    private Map<String, Long> toCountMap(List<Object[]> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }
}
