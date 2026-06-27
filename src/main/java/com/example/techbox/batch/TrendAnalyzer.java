package com.example.techbox.batch;

import com.example.techbox.domain.Article;
import com.example.techbox.domain.TrendingKeyword;
import com.example.techbox.repository.TrendingKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendAnalyzer {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "this", "that", "these", "those",
            "its", "their", "our", "your", "his", "her", "new", "how", "why",
            "what", "when", "where", "which", "not", "all", "use", "get", "via"
    );

    private final TrendingKeywordRepository trendingKeywordRepository;

    @Transactional
    public void analyze(List<Article> articles) {
        if (articles.isEmpty()) return;

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        Map<String, Long> wordCounts = articles.stream()
                .flatMap(article -> extractKeywords(article).stream())
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        wordCounts.forEach((keyword, count) -> {
            trendingKeywordRepository.findByKeywordAndDate(keyword, today)
                    .ifPresentOrElse(
                            existing -> {
                                existing.updateCount(existing.getCount() + count.intValue());
                                trendingKeywordRepository.save(existing);
                            },
                            () -> {
                                int prevCount = trendingKeywordRepository
                                        .findByKeywordAndDate(keyword, yesterday)
                                        .map(TrendingKeyword::getCount)
                                        .orElse(0);

                                double growthRate = prevCount == 0 ? 0.0
                                        : (double) (count - prevCount) / prevCount;

                                trendingKeywordRepository.save(TrendingKeyword.builder()
                                        .keyword(keyword)
                                        .count(count.intValue())
                                        .prevCount(prevCount)
                                        .growthRate(prevCount == 0 ? null : growthRate)
                                        .date(today)
                                        .build());
                            }
                    );
        });

        log.info("Trend analysis complete: {} keywords processed", wordCounts.size());
    }

    private List<String> extractKeywords(Article article) {
        List<String> keywords = new ArrayList<>();

        if (article.getTitle() != null) {
            Arrays.stream(article.getTitle().split("[^a-zA-Z0-9\\u3040-\\u9FFF\\u30A0-\\u30FF]"))
                    .map(String::toLowerCase)
                    .filter(w -> w.length() >= 3 && !STOP_WORDS.contains(w))
                    .forEach(keywords::add);
        }

        if (article.getTags() != null) {
            Arrays.stream(article.getTags())
                    .map(String::toLowerCase)
                    .filter(t -> t.length() >= 2)
                    .forEach(keywords::add);
        }

        return keywords;
    }
}
