package com.example.techbox.batch;

import com.example.techbox.config.AppProperties;
import com.example.techbox.domain.Article;
import com.example.techbox.repository.ArticleRepository;
import com.example.techbox.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotifierService {

    private static final String LINE_PUSH_URL = "https://api.line.me/v2/bot/message/push";
    private static final int TOP_ARTICLES_COUNT = 5;

    private final RestClient restClient;
    private final AppProperties appProperties;
    private final ArticleRepository articleRepository;
    private final SummaryRepository summaryRepository;

    @Transactional
    public void sendDailyDigest() {
        List<Article> topArticles = articleRepository
                .findUnnotifiedOrderByScoreDesc(PageRequest.of(0, TOP_ARTICLES_COUNT));

        if (topArticles.isEmpty()) {
            log.warn("No articles to notify");
            return;
        }

        String message = formatDailyDigest(topArticles);
        sendPushMessage(message);

        topArticles.forEach(Article::markNotified);
        articleRepository.saveAll(topArticles);

        log.info("LINE daily digest sent: {} articles", topArticles.size());
    }

    public void sendErrorNotification(String errorMessage) {
        try {
            sendPushMessage("[TechBox] バッチエラー: " + errorMessage);
        } catch (Exception e) {
            log.error("Failed to send error notification to LINE", e);
        }
    }

    private String formatDailyDigest(List<Article> articles) {
        StringBuilder sb = new StringBuilder("[TechBox] 本日のITニュース\n\n");

        for (int i = 0; i < articles.size(); i++) {
            Article article = articles.get(i);
            sb.append(i + 1).append(". ").append(article.getTitle()).append("\n");

            summaryRepository.findByArticleId(article.getId()).ifPresent(summary ->
                    sb.append(summary.getSummaryJa()).append("\n")
            );

            sb.append(article.getUrl()).append("\n\n");
        }

        return sb.toString().trim();
    }

    private void sendPushMessage(String text) {
        String token = appProperties.getLine().getChannelAccessToken();
        String userId = appProperties.getLine().getUserId();

        Map<String, Object> body = Map.of(
                "to", userId,
                "messages", List.of(Map.of("type", "text", "text", text))
        );

        restClient.post()
                .uri(LINE_PUSH_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
