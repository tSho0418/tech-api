package com.example.techbox.api.dto;

import com.example.techbox.domain.Article;
import com.example.techbox.domain.Summary;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ArticleResponse {

    private UUID id;
    private String sourceName;
    private String title;
    private String url;
    private String author;
    private Integer score;
    private String[] tags;
    private String language;
    private OffsetDateTime publishedAt;
    private OffsetDateTime fetchedAt;
    private boolean notified;
    private SummaryResponse summary;

    public static ArticleResponse from(Article article, Summary summary) {
        return ArticleResponse.builder()
                .id(article.getId())
                .sourceName(article.getSource().getName())
                .title(article.getTitle())
                .url(article.getUrl())
                .author(article.getAuthor())
                .score(article.getScore())
                .tags(article.getTags())
                .language(article.getLanguage())
                .publishedAt(article.getPublishedAt())
                .fetchedAt(article.getFetchedAt())
                .notified(article.isNotified())
                .summary(summary != null ? SummaryResponse.from(summary) : null)
                .build();
    }

    public static ArticleResponse from(Article article) {
        return from(article, null);
    }
}
