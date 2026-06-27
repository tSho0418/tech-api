package com.example.techbox.batch;

import com.example.techbox.config.AppProperties;
import com.example.techbox.domain.Article;
import com.example.techbox.domain.Summary;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.CollectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
            "あなたはITエンジニア向けのニュース要約AIです。与えられた記事一覧を日本語で簡潔に要約してください。";

    private final RestClient restClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    // 日次クォータ上限に達した場合にスロー。BatchJobService でキャッチして残ソースをスキップ
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

    /**
     * 複数記事を一括して1回のGemini APIリクエストで要約する。
     * ソース別にまとめて呼び出すことでAPI使用回数を削減できる。
     */
    public List<Summary> summarizeBatch(List<Article> articles) {
        if (articles.isEmpty()) return List.of();

        String prompt = buildBatchPrompt(articles);
        Exception lastException = null;
        long waitMs = 0;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (waitMs > 0) {
                    log.info("Waiting {}s before Gemini retry {}/{}", waitMs / 1000, attempt, MAX_RETRIES);
                    Thread.sleep(waitMs);
                }

                String responseText = callGemini(prompt);
                List<GeminiSummaryResult> results = parseBatchResponse(responseText);
                return buildSummaries(articles, results);

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
                log.warn("Batch summarization attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }
        }
        throw new RuntimeException("Batch summarization failed after " + MAX_RETRIES + " retries", lastException);
    }

    private List<Summary> buildSummaries(List<Article> articles, List<GeminiSummaryResult> results) {
        // index(1始まり) → result のマップを作成
        Map<Integer, GeminiSummaryResult> resultByIndex = results.stream()
                .collect(Collectors.toMap(GeminiSummaryResult::index, Function.identity(), (a, b) -> a));

        List<Summary> summaries = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            GeminiSummaryResult result = resultByIndex.get(i + 1);
            if (result == null) {
                log.warn("No summary result for article index {}: {}", i + 1, articles.get(i).getTitle());
                continue;
            }
            summaries.add(Summary.builder()
                    .article(articles.get(i))
                    .summaryJa(result.summaryJa())
                    .reasonJa(result.reasonJa())
                    .qualityScore(result.qualityScore())
                    .modelUsed(MODEL)
                    .build());
        }
        return summaries;
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
                long retryMs = parseRetryDelayMs(body);
                throw new RateLimitException("Gemini RPM rate limit hit", retryMs);
            }
            throw e;
        }
    }

    private List<GeminiSummaryResult> parseBatchResponse(String responseText) {
        try {
            // Gemini が application/json を指定しても稀にコードブロックで囲む場合があるためフォールバック処理
            String json = extractJson(responseText);
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, GeminiSummaryResult.class);
            return objectMapper.readValue(json, listType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini batch response: " + responseText, e);
        }
    }

    private String extractJson(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            int closing = trimmed.lastIndexOf("```");
            if (newline > 0 && closing > newline) {
                return trimmed.substring(newline + 1, closing).strip();
            }
        }
        return trimmed;
    }

    private String buildBatchPrompt(List<Article> articles) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下の ").append(articles.size()).append(" 件のITニュース記事を日本語で要約してください。\n\n");

        IntStream.range(0, articles.size()).forEach(i ->
                sb.append(i + 1).append(". タイトル: ").append(articles.get(i).getTitle()).append("\n")
        );

        sb.append("""

                各記事について以下のJSON配列形式のみで回答してください（index は記事番号に対応）:
                [
                  {"index": 1, "summary_ja": "3〜5行の日本語要約", "reason_ja": "なぜ今注目されているかを1行で", "quality_score": 0.9},
                  ...
                ]
                """);

        return sb.toString();
    }

    private long parseRetryDelayMs(String body) {
        Matcher matcher = RETRY_DELAY_PATTERN.matcher(body);
        if (matcher.find()) {
            double seconds = Double.parseDouble(matcher.group(1));
            return (long) (seconds * 1000) + 1_000;
        }
        return DEFAULT_RETRY_DELAY_MS;
    }

    private record GeminiResponse(List<GeminiCandidate> candidates) {}
    private record GeminiCandidate(GeminiContent content) {}
    private record GeminiContent(List<GeminiPart> parts) {}
    private record GeminiPart(String text) {}

    private record GeminiSummaryResult(
            int index,
            @JsonProperty("summary_ja") String summaryJa,
            @JsonProperty("reason_ja") String reasonJa,
            @JsonProperty("quality_score") Double qualityScore) {}
}
