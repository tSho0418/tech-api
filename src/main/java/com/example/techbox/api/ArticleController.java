package com.example.techbox.api;

import com.example.techbox.api.dto.ArticleResponse;
import com.example.techbox.api.dto.common.PageResponse;
import com.example.techbox.domain.Article;
import com.example.techbox.domain.Summary;
import com.example.techbox.repository.ArticleRepository;
import com.example.techbox.repository.SummaryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleRepository articleRepository;
    private final SummaryRepository summaryRepository;

    @GetMapping
    public PageResponse<ArticleResponse> getArticles(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDate targetDate = date != null ? date : LocalDate.now();
        OffsetDateTime start = targetDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = start.plusDays(1);

        // タグフィルターがある場合は全件取得してメモリフィルタリング（1日最大~90件）
        if (tag != null) {
            List<Article> all = articleRepository.findTodayArticles(start, end);
            List<Article> filtered = all.stream()
                    .filter(a -> source == null || source.equals(a.getSource().getName()))
                    .filter(a -> a.getTags() != null && Arrays.asList(a.getTags()).contains(tag))
                    .toList();
            return toPageResponse(filtered, page, size);
        }

        // ソースフィルターのみ
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fetchedAt"));
        Page<Article> articlePage = source != null
                ? articleRepository.findBySourceNameAndDateRange(source, start, end, pageable)
                : articleRepository.findByDateRange(start, end, pageable);

        return PageResponse.from(articlePage.map(a -> {
            Summary summary = summaryRepository.findByArticleId(a.getId()).orElse(null);
            return ArticleResponse.from(a, summary);
        }));
    }

    @GetMapping("/today")
    public List<ArticleResponse> getTodayArticles() {
        OffsetDateTime start = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = start.plusDays(1);

        return articleRepository.findTodayArticles(start, end).stream()
                .map(a -> {
                    Summary summary = summaryRepository.findByArticleId(a.getId()).orElse(null);
                    return ArticleResponse.from(a, summary);
                })
                .toList();
    }

    @GetMapping("/{id}")
    public ArticleResponse getArticle(@PathVariable UUID id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Article not found: " + id));
        Summary summary = summaryRepository.findByArticleId(id).orElse(null);
        return ArticleResponse.from(article, summary);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Article not found: " + id));
        article.markNotified();
        articleRepository.save(article);
        return ResponseEntity.noContent().build();
    }

    private PageResponse<ArticleResponse> toPageResponse(List<Article> articles, int page, int size) {
        int total = articles.size();
        int from = page * size;
        int to = Math.min(from + size, total);
        List<Article> pageContent = from >= total ? List.of() : articles.subList(from, to);

        List<ArticleResponse> responses = pageContent.stream()
                .map(a -> {
                    Summary summary = summaryRepository.findByArticleId(a.getId()).orElse(null);
                    return ArticleResponse.from(a, summary);
                })
                .toList();

        Page<ArticleResponse> springPage = new PageImpl<>(responses, PageRequest.of(page, size), total);
        return PageResponse.from(springPage);
    }
}
