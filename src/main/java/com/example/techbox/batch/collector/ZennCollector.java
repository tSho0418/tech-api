package com.example.techbox.batch.collector;

import com.example.techbox.domain.Article;
import com.example.techbox.domain.Source;
import com.example.techbox.repository.SourceRepository;
import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ZennCollector implements ArticleCollector {

    private static final String SOURCE_NAME = "Zenn";
    private static final String RSS_URL = "https://zenn.dev/feed";
    private static final int LIMIT = 20;

    private final SourceRepository sourceRepository;

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<Article> collect() {
        Source source = sourceRepository.findByName(SOURCE_NAME)
                .orElseThrow(() -> new IllegalStateException("Source not found: " + SOURCE_NAME));

        try (InputStream is = new URI(RSS_URL).toURL().openStream();
             XmlReader reader = new XmlReader(is)) {

            SyndFeed feed = new SyndFeedInput().build(reader);

            return feed.getEntries().stream()
                    .limit(LIMIT)
                    .filter(entry -> entry.getTitle() != null && entry.getLink() != null)
                    .map(entry -> {
                        OffsetDateTime published = entry.getPublishedDate() != null
                                ? entry.getPublishedDate().toInstant().atOffset(ZoneOffset.UTC)
                                : null;
                        String[] tags = entry.getCategories().stream()
                                .map(SyndCategory::getName)
                                .toArray(String[]::new);

                        return Article.builder()
                                .source(source)
                                .title(entry.getTitle())
                                .url(entry.getLink())
                                .author(entry.getAuthor())
                                .tags(tags.length > 0 ? tags : null)
                                .language("ja")
                                .publishedAt(published)
                                .build();
                    })
                    .toList();

        } catch (Exception e) {
            log.error("Zenn RSS fetch failed", e);
            return List.of();
        }
    }
}
