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
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummarizerService {

    private static final String MODEL = "gemini-2.5-flash";
    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_RETRIES = 3;
    private static final String SYSTEM_PROMPT =
            "あなたはITエンジニア向けのニュース要約AIです。与えられた記事を日本語で簡潔に要約してください。";

    private final RestClient restClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public Summary summarize(Article article) {
        String prompt = buildPrompt(article.getTitle());
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 1) {
                    long waitMs = (long) Math.pow(2, attempt - 1) * 1000;
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

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Summarization interrupted", e);
            } catch (Exception e) {
                lastException = e;
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
