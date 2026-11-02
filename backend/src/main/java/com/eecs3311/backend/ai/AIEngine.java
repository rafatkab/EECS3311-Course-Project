package com.eecs3311.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Stage 1 Singleton: one pooled HTTP client and generation limit for all agents. */
public final class AIEngine {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Semaphore available = new Semaphore(2);
    private AIEngine() { }
    private static class Holder { private static final AIEngine INSTANCE = new AIEngine(); }
    public static AIEngine getInstance() { return Holder.INSTANCE; }

    public record Completion(String content, long inputTokens, long outputTokens) { }

    public Completion generate(String context, String instruction, AiSettings settings) {
        return generate(context, instruction, settings, Duration.ofSeconds(settings.timeoutSeconds()));
    }

    public Completion generate(String context, String instruction, AiSettings settings, Duration timeout) {
        return generate(context, instruction, settings, timeout, null);
    }

    public Completion generate(String context, String instruction, AiSettings settings, Duration timeout, JsonNode schema) {
        var endpoint = settings.endpoint();
        if (!available.tryAcquire()) throw new AiFailure(429, "The AI service is busy. Please try again shortly.");
        try {
            var body = json.createObjectNode();
            body.put("model", settings.model());
            body.put("stream", false);
            body.put(settings.tokenLimitField(), settings.maxOutputTokens());
            body.set("response_format", schema == null ? json.valueToTree(Map.of("type", "json_object"))
                    : json.valueToTree(Map.of("type", "json_schema", "json_schema", Map.of("name", "document_summary", "strict", true, "schema", schema))));
            body.set("messages", json.valueToTree(List.of(
                    Map.of("role", "system", "content", instruction + (schema == null ? "" : "\nRequired output JSON schema: " + schema)), Map.of("role", "user", "content", context))));
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
                request.header("Authorization", "Bearer " + settings.apiKey());
            }
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 401 || status == 403) throw new AiFailure(503, "AI provider authentication failed. Check the backend's local API key.");
                if (status == 429) throw new AiFailure(429, "The AI provider's rate or quota limit was reached. Try again later.");
                if (status >= 400) throw new AiFailure(502, "The AI provider rejected the request. Check the model and provider configuration.");
                byte[] bytes = response.body();
                if (bytes.length > 256 * 1024) throw new AiFailure(502, "The AI provider returned an oversized response.");
                JsonNode root = json.readTree(new String(bytes, StandardCharsets.UTF_8));
                JsonNode choice = root == null ? null : root.path("choices").path(0);
                if (choice == null || !"stop".equals(choice.path("finish_reason").asText())) {
                    throw new AiFailure(502, "The AI response was incomplete or refused. Try a suitable model or output limit.");
                }
                JsonNode content = choice.path("message").path("content");
                if (!content.isTextual() || content.asText().isBlank()) throw new AiFailure(502, "The AI provider returned no summary content.");
                long input = root.path("usage").path("prompt_tokens").asLong(-1);
                long output = root.path("usage").path("completion_tokens").asLong(-1);
                return new Completion(content.asText(), input, output);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AiFailure(504, "The AI provider timed out. Try again or use a faster model.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiFailure(503, "Summary generation was interrupted. No new summary was saved.");
        } catch (IOException exception) {
            throw new AiFailure(502, "Cannot reach the AI provider or read its response. Check that it is running and correctly configured.");
        } finally {
            available.release();
        }
    }
}
