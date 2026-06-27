package com.example.techbox.batch;

import com.example.techbox.config.AppProperties;
import com.example.techbox.domain.Article;
import com.example.techbox.domain.Summary;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummarizerService {

    private static final String MODEL = "gemini-2.5-flash";
    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 60_000;
    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile("\"retryDelay\":\\s*\"([0-9.]+)s\"");
    private static final String SYSTEM_PROMPT =
            "あなたはITエンジニア向けのニュース要約AIです。与えられた記事を日本語で簡潔に要約してください。";

    private final RestClient restClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    // 日次クォータ上限に達した場合にスローされる。BatchJobService でキャッチして残記事をスキップ
    public static class DailyQuotaExceededException extends RuntimeException {
        public DailyQuotaExceededException(String message) {
            super(message);
        }
    }

    private static class RateLimitException extends RuntimeException {
        private final long retryAfterMs;

        RateLimitException(String message, long retryAfterMs) {
            super(message);
            this.retryAfterMs = retryAfterMs;
        }

        long getRetryAfterMs() {
            return retryAfterMs;
        }
    }

    public Summary summarize(Article article) {
        String prompt = buildPrompt(article.getTitle());
        Exception lastException = null;
        long waitMs = 0;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (waitMs > 0) {
                    log.info("Waiting {}s before Gemini retry {}/{}", waitMs / 1000, attempt, MAX_RETRIES);
                    Thread.sleep(waitMs);
                }

                String responseText = callGemini(prompt);
                GeminiSummaryResult result = objectMapper.readValue(responseText, GeminiSummaryResult.class);

                return Summary.builder()
                        .article(article)
                        .summaryJa(result.summaryJa())
                        .reasonJa(result.reasonJa())
                        .qualityScore(result.qualityScore())
                        .modelUsed(MODEL)
                        .build();

            } catch (DailyQuotaExceededException e) {
                throw e; // リトライ不可。即座に呼び出し元へ伝播
            } catch (RateLimitException e) {
                lastException = e;
                waitMs = e.getRetryAfterMs();
                log.warn("Rate limited (RPM). Waiting {}s before retry.", waitMs / 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Summarization interrupted", e);
            } catch (Exception e) {
                lastException = e;
                waitMs = (long) Math.pow(2, attempt - 1) * 1000;
                log.warn("Summarization attempt {}/{} failed for article {}: {}",
                        attempt, MAX_RETRIES, article.getId(), e.getMessage());
            }
        }
        throw new RuntimeException("Summarization failed after " + MAX_RETRIES + " retries", lastException);
    }

    private String callGemini(String prompt) {
        String apiKey = appProperties.getGemini().getApiKey();
        String url = API_BASE + MODEL + ":generateContent?key=" + apiKey;

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        try {
            GeminiResponse response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                throw new RuntimeException("Empty response from Gemini API");
            }
            return response.candidates().get(0).content().parts().get(0).text();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                String body = e.getResponseBodyAsString();
                if (body.contains("PerDay")) {
                    log.error("Gemini daily quota exceeded: {}", body);
                    throw new DailyQuotaExceededException("Daily Gemini API quota exceeded");
                }
                // RPM 制限: API が指定する retryDelay を使用
                long retryMs = parseRetryDelayMs(body);
                throw new RateLimitException("Gemini RPM rate limit hit", retryMs);
            }
            throw e;
        }
    }

    private long parseRetryDelayMs(String body) {
        Matcher matcher = RETRY_DELAY_PATTERN.matcher(body);
        if (matcher.find()) {
            double seconds = Double.parseDouble(matcher.group(1));
            return (long) (seconds * 1000) + 1_000; // 1秒のバッファを追加
        }
        return DEFAULT_RETRY_DELAY_MS;
    }

    private String buildPrompt(String title) {
        return """
                タイトル: %s
                本文（または概要）: %s

                以下のJSON形式のみで回答してください:
                {
                  "summary_ja": "3〜5行の日本語要約",
                  "reason_ja": "なぜ今注目されているかを1行で",
                  "quality_score": 0.0〜1.0の信頼度スコア
                }
                """.formatted(title, title);
    }

    private record GeminiResponse(List<GeminiCandidate> candidates) {}
    private record GeminiCandidate(GeminiContent content) {}
    private record GeminiContent(List<GeminiPart> parts) {}
    private record GeminiPart(String text) {}

    private record GeminiSummaryResult(
            @JsonProperty("summary_ja") String summaryJa,
            @JsonProperty("reason_ja") String reasonJa,
            @JsonProperty("quality_score") Double qualityScore) {}
}
