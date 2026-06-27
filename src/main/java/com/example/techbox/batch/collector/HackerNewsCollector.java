package com.example.techbox.batch.collector;

import com.example.techbox.domain.Article;
import com.example.techbox.domain.Source;
import com.example.techbox.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class HackerNewsCollector implements ArticleCollector {

    private static final String SOURCE_NAME = "Hacker News";
    private static final String BASE_URL = "https://hacker-news.firebaseio.com/v0";
    private static final int LIMIT = 30;

    private final RestClient restClient;
    private final SourceRepository sourceRepository;

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<Article> collect() {
        Source source = sourceRepository.findByName(SOURCE_NAME)
                .orElseThrow(() -> new IllegalStateException("Source not found: " + SOURCE_NAME));

        int[] ids = restClient.get()
                .uri(BASE_URL + "/topstories.json")
                .retrieve()
                .body(int[].class);

        if (ids == null) return List.of();

        return Arrays.stream(ids)
                .limit(LIMIT)
                .mapToObj(id -> {
                    try {
                        return restClient.get()
                                .uri(BASE_URL + "/item/" + id + ".json")
                                .retrieve()
                                .body(HackerNewsItem.class);
                    } catch (Exception e) {
                        log.warn("Failed to fetch HN item {}", id, e);
                        return null;
                    }
                })
                .filter(item -> item != null
                        && "story".equals(item.type())
                        && item.url() != null
                        && item.title() != null)
                .map(item -> Article.builder()
                        .source(source)
                        .title(item.title())
                        .url(item.url())
                        .author(item.by())
                        .score(item.score())
                        .language("en")
                        .publishedAt(item.time() != null
                                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(item.time()), ZoneOffset.UTC)
                                : null)
                        .build())
                .filter(Objects::nonNull)
                .toList();
    }

    private record HackerNewsItem(Long id, String title, String url, String by,
                                  Integer score, Long time, String type) {}
}
