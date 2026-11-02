package com.eecs3311.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

/** Bound from backend-only local configuration. Never include the key in logs. */
@ConfigurationProperties("study.ai")
public record AiSettings(String baseUrl, String model, String apiKey, String tokenLimitField,
                         int timeoutSeconds, int maxOutputTokens) {
    public URI endpoint() {
        if (model == null || model.isBlank() || model.contains("REPLACE_")) {
            throw new AiFailure(503, "Configure study.ai.model and study.ai.base-url in the backend's local configuration first.");
        }
        try {
            URI base = URI.create(baseUrl);
            String host = base.getHost();
            boolean local = java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host == null ? "" : host);
            if (host == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || !("https".equals(base.getScheme()) || (local && "http".equals(base.getScheme())))) {
                throw new IllegalArgumentException();
            }
            if (!local && (apiKey == null || apiKey.isBlank() || apiKey.contains("REPLACE_"))) {
                throw new AiFailure(503, "Configure study.ai.api-key for the hosted AI provider.");
            }
            if (model.length() > 255 || tokenLimitField == null || !java.util.Set.of("max_tokens", "max_completion_tokens").contains(tokenLimitField)
                    || timeoutSeconds < 1 || timeoutSeconds > 120 || maxOutputTokens < 128 || maxOutputTokens > 4096) {
                throw new IllegalArgumentException();
            }
            return URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        } catch (IllegalArgumentException exception) {
            throw new AiFailure(503, "Invalid AI configuration. Use HTTPS for hosted providers or HTTP on localhost; check the documented limits.");
        }
    }

    @Override public String toString() { return "AiSettings[credentials redacted]"; }
}
