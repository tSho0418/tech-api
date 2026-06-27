package com.example.techbox.batch.collector;

import com.example.techbox.domain.Article;
import com.example.techbox.domain.Source;
import com.example.techbox.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubTrendingCollector implements ArticleCollector {

    private static final String SOURCE_NAME = "GitHub Trending";
    private static final String TRENDING_URL = "https://github.com/trending";

    private final SourceRepository sourceRepository;

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<Article> collect() {
        Source source = sourceRepository.findByName(SOURCE_NAME)
                .orElseThrow(() -> new IllegalStateException("Source not found: " + SOURCE_NAME));

        try {
            Document doc = Jsoup.connect(TRENDING_URL)
                    .userAgent("Mozilla/5.0 (compatible; TechBox/1.0)")
                    .timeout(10_000)
                    .get();

            return doc.select("article.Box-row").stream()
                    .map(row -> parseRow(row, source))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("GitHub Trending scraping failed", e);
            return List.of();
        }
    }

    private Article parseRow(Element row, Source source) {
        Element repoLink = row.selectFirst("h2 a");
        if (repoLink == null) return null;

        String relPath = repoLink.attr("href").trim();
        String repoUrl = "https://github.com" + relPath;
        String repoName = relPath.replaceFirst("^/", "").trim();

        Element descriptionEl = row.selectFirst("p");
        String description = descriptionEl != null ? descriptionEl.text().trim() : "";

        String title = description.isBlank() ? repoName : repoName + " — " + description;

        return Article.builder()
                .source(source)
                .title(title)
                .url(repoUrl)
                .language("en")
                .build();
    }
}
