package com.example.techbox.batch;

import com.example.techbox.batch.collector.ArticleCollector;
import com.example.techbox.domain.Article;
import com.example.techbox.domain.BatchLog;
import com.example.techbox.repository.ArticleRepository;
import com.example.techbox.repository.BatchLogRepository;
import com.example.techbox.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobService {

    private static final long SUMMARIZE_INTERVAL_MS = 4_000;

    private final List<ArticleCollector> collectors;
    private final SummarizerService summarizerService;
    private final TrendAnalyzer trendAnalyzer;
    private final NotifierService notifierService;
    private final ArticleRepository articleRepository;
    private final SummaryRepository summaryRepository;
    private final BatchLogRepository batchLogRepository;

    @Async
    public void runBatch() {
        BatchLog batchLog = batchLogRepository.save(BatchLog.builder()
                .batchType("full")
                .status("running")
                .build());

        int fetched = 0, skipped = 0, errors = 0;
        String errorDetail = null;

        try {
            // 1. 各ソースを並列収集
            List<CompletableFuture<List<Article>>> futures = collectors.stream()
                    .map(collector -> CompletableFuture.supplyAsync(() -> {
                        try {
                            List<Article> articles = collector.collect();
                            log.info("[{}] collected {} articles", collector.getSourceName(), articles.size());
                            return articles;
                        } catch (Exception e) {
                            log.error("[{}] collection failed", collector.getSourceName(), e);
                            return List.<Article>of();
                        }
                    }))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<Article> allArticles = futures.stream()
                    .flatMap(f -> f.join().stream())
                    .toList();

            if (allArticles.isEmpty()) {
                batchLog.complete("fail", 0, 0, 0, "All collectors returned empty");
                batchLogRepository.save(batchLog);
                notifierService.sendErrorNotification("全コレクターの収集が失敗しました");
                return;
            }

            // 2. 重複排除して保存
            List<Article> newArticles = new ArrayList<>();
            for (Article article : allArticles) {
                if (articleRepository.existsByUrl(article.getUrl())) {
                    skipped++;
                } else {
                    newArticles.add(articleRepository.save(article));
                    fetched++;
                }
            }
            log.info("Articles: fetched={}, skipped={}", fetched, skipped);

            // 3. Gemini API で要約（RPM制限対策: 4秒間隔）
            for (Article article : newArticles) {
                try {
                    summaryRepository.save(summarizerService.summarize(article));
                    Thread.sleep(SUMMARIZE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Batch interrupted during summarization", e);
                } catch (Exception e) {
                    log.error("Summarization failed for article {}", article.getId(), e);
                    errors++;
                }
            }

            // 4. トレンドキーワード集計
            trendAnalyzer.analyze(newArticles);

            // 5. LINE 通知
            notifierService.sendDailyDigest();

            // 6. バッチログ完了記録
            String status = errors == 0 ? "success" : "partial";
            batchLog.complete(status, fetched, skipped, errors, null);
            batchLogRepository.save(batchLog);
            log.info("Batch completed: status={}, fetched={}, skipped={}, errors={}", status, fetched, skipped, errors);

        } catch (Exception e) {
            log.error("Batch job failed unexpectedly", e);
            errorDetail = e.getMessage();
            batchLog.complete("fail", fetched, skipped, errors, errorDetail);
            batchLogRepository.save(batchLog);
            notifierService.sendErrorNotification("バッチ処理が失敗しました: " + errorDetail);
        }
    }
}
