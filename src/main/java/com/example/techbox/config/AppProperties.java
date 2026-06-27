package com.example.techbox.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Gemini gemini = new Gemini();
    private final Line line = new Line();

    @Getter
    @Setter
    public static class Security {
        private String apiKey;
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigin;
    }

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey;
    }

    @Getter
    @Setter
    public static class Line {
        private String channelAccessToken;
        private String userId;
    }
}
