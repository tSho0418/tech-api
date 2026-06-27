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
public class DevToCollector implements ArticleCollector {

    private static final String SOURCE_NAME = "dev.to";
    private static final String API_URL = "https://dev.to/api/articles?per_page=20&top=7";

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

        DevToArticle[] articles = restClient.get()
                .uri(API_URL)
                .retrieve()
                .body(DevToArticle[].class);

        if (articles == null) return List.of();

        return Arrays.stream(articles)
                .filter(a -> a.title() != null && a.url() != null)
                .map(a -> Article.builder()
                        .source(source)
                        .title(a.title())
                        .url(a.url())
                        .author(a.user() != null ? a.user().username() : null)
                        .tags(parseTagList(a.tagList()))
                        .language("en")
                        .publishedAt(parseDateTime(a.publishedAt()))
                        .build())
                .toList();
    }

    private String[] parseTagList(String tagList) {
        if (tagList == null || tagList.isBlank()) return null;
        String[] tags = tagList.split(",\\s*");
        return tags.length > 0 ? tags : null;
    }

    private OffsetDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return OffsetDateTime.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }

    private record DevToArticle(
            String title,
            String url,
            @JsonProperty("published_at") String publishedAt,
            @JsonProperty("tag_list") String tagList,
            DevToUser user) {}

    private record DevToUser(String username) {}
}
