package com.example.techbox.batch.collector;

import com.example.techbox.domain.Article;
import com.example.techbox.domain.Source;
import com.example.techbox.repository.SourceRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class QiitaCollector implements ArticleCollector {

    private static final String SOURCE_NAME = "Qiita";
    private static final String API_URL = "https://qiita.com/api/v2/items?per_page=20";

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

        QiitaItem[] items = restClient.get()
                .uri(API_URL)
                .retrieve()
                .body(QiitaItem[].class);

        if (items == null) return List.of();

        return Arrays.stream(items)
                .filter(item -> item.title() != null && item.url() != null)
                .map(item -> Article.builder()
                        .source(source)
                        .title(item.title())
                        .url(item.url())
                        .author(item.user() != null ? item.user().id() : null)
                        .tags(extractTags(item.tags()))
                        .language("ja")
                        .publishedAt(parseDateTime(item.createdAt()))
                        .build())
                .toList();
    }

    private String[] extractTags(List<QiitaTag> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return tags.stream().map(QiitaTag::name).toArray(String[]::new);
    }

    private OffsetDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return OffsetDateTime.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }

    private record QiitaItem(
            String title,
            String url,
            @JsonProperty("created_at") String createdAt,
            QiitaUser user,
            List<QiitaTag> tags) {}

    private record QiitaUser(String id) {}

    private record QiitaTag(String name) {}
}
