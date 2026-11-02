package com.eecs3311.backend.ai;

import com.eecs3311.backend.resource.StudyResourceFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Scripted HTTP provider verifies the real request path, not LLM output quality. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:summary-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SummaryApiTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicReference<String> MODE = new AtomicReference<>("valid");
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<String> LAST_REQUEST = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> ENTERED = new AtomicReference<>(new CountDownLatch(1));
    private static final HttpServer PROVIDER;
    static {
        try {
            PROVIDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PROVIDER.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable); thread.setDaemon(true); return thread;
            }));
            PROVIDER.createContext("/v1/chat/completions", exchange -> {
                int call = CALLS.incrementAndGet();
                String mode = MODE.get();
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                LAST_REQUEST.set(request);
                ENTERED.get().countDown();
                if (mode.equals("timeout") || mode.equals("slow")) {
                    try { Thread.sleep(mode.equals("timeout") ? 1600 : 400); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                }
                int status = mode.equals("auth") ? 401 : mode.equals("rate") ? 429 : 200;
                String response;
                if (status != 200) response = "{\"error\":{\"message\":\"secret-provider-diagnostic\"}}";
                else {
                    var body = JSON.readTree(request);
                    String content = JSON.writeValueAsString(Map.of("summary", "The document explains how repository interfaces separate application behavior from SQL storage details.", "evidence", List.of(mode.equals("ungrounded") ? 9999 : 1)));
                    if (mode.equals("invalid") || (mode.equals("repair") && call == 1)) content = "not valid JSON";
                    var result = JSON.createObjectNode();
                    result.set("choices", JSON.valueToTree(List.of(Map.of("finish_reason", mode.equals("truncated") ? "length" : "stop", "message", Map.of("content", content)))));
                    if (!mode.equals("no-usage")) result.set("usage", JSON.valueToTree(Map.of("prompt_tokens", 30, "completion_tokens", 12)));
                    response = result.toString();
                }
                try {
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (java.io.IOException ignored) { /* Timed-out test clients may disconnect. */ }
                finally { exchange.close(); }
            });
            PROVIDER.start();
        } catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    @DynamicPropertySource static void provider(DynamicPropertyRegistry registry) {
        registry.add("study.ai.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/v1");
        registry.add("study.ai.model", () -> "fixture-model");
        registry.add("study.ai.api-key", () -> "test-provider-key");
        registry.add("study.ai.timeout-seconds", () -> 1);
    }
    @AfterAll static void stopProvider() { PROVIDER.stop(0); }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired StudyResourceFactory factory;
    private String courseId;
    private String materialId;
    private String path;

    @BeforeEach void fixture() throws Exception {
        MODE.set("valid"); CALLS.set(0); ENTERED.set(new CountDownLatch(1));
        jdbc.update("DELETE FROM courses");
        var course = mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Software Design\",\"description\":\"Patterns\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-12-20\"}"))
                .andExpect(status().isCreated()).andReturn();
        courseId = json.readTree(course.getResponse().getContentAsString()).path("courseId").asText();
        materialId = upload("The Repository pattern separates application behavior from SQL storage details. Prepared statements safely bind data to SQL queries.");
        path = "/api/courses/" + courseId + "/materials/" + materialId + "/summary";
    }
    private String upload(String text) throws Exception {
        var response = mvc.perform(multipart("/api/courses/" + courseId + "/materials")
                .file(new MockMultipartFile("files", "lecture.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                .with(user("alice")).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get(0).path("materialId").asText();
    }

    @Test void generatesStoresAndRetrievesOwnedSummary() throws Exception {
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryText").value(org.hamcrest.Matchers.containsString("repository interfaces")))
                .andExpect(jsonPath("$.model").value("fixture-model"))
                .andExpect(jsonPath("$.sections").value(1)).andExpect(jsonPath("$.modelCalls").value(1))
                .andExpect(jsonPath("$.inputTokens").value(30)).andExpect(jsonPath("$.outputTokens").value(12))
                .andExpect(jsonPath("$.evidence[0]").isString()).andExpect(jsonPath("$.sourceHash").doesNotExist());
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM summaries", Integer.class)).isEqualTo(1);
        var request = JSON.readTree(LAST_REQUEST.get());
        assertThat(request.path("messages").get(0).path("content").asText()).contains("untrusted source data");
        assertThat(request.path("messages").get(1).path("content").asText()).contains("Repository pattern");
        assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(request.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
    }

    @Test void cachedSummaryNeedsNoProviderAndFailedRegenerationPreservesIt() throws Exception {
        var first = mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isOk()).andReturn();
        String id = json.readTree(first.getResponse().getContentAsString()).path("resourceId").asText();
        MODE.set("auth");
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.resourceId").value(id));
        assertThat(CALLS.get()).isEqualTo(1);
        mvc.perform(post(path + "?regenerate=true").with(user("alice")).with(csrf())).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-provider-diagnostic"))));
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.resourceId").value(id));
        MODE.set("valid");
        mvc.perform(post(path + "?regenerate=true").with(user("alice")).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(org.hamcrest.Matchers.not(id)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM summaries", Integer.class)).isEqualTo(1);
    }

    @Test void plansSectionSummariesAndFinalSynthesis() throws Exception {
        String id = upload("Repository interfaces keep storage separate from application behavior.\n".repeat(120));
        mvc.perform(post("/api/courses/" + courseId + "/materials/" + id + "/summary").with(user("alice")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sections").value(2)).andExpect(jsonPath("$.modelCalls").value(3));
        assertThat(CALLS.get()).isEqualTo(3);
    }

    @Test void avoidsTinyTrailingSectionsAndBoundsMaximumDocumentPlan() throws Exception {
        String notes = "Repository interfaces separate application behavior from SQL storage details.\n".repeat(500);
        String nearBoundary = upload(notes.substring(0, ContentAgent.CHUNK_CHARS + 1));
        mvc.perform(post("/api/courses/" + courseId + "/materials/" + nearBoundary + "/summary").with(user("alice")).with(csrf()))
                .andExpect(status().isOk());
        CALLS.set(0);
        String maximum = upload(notes.substring(0, ContentAgent.MAX_INPUT_CHARS));
        mvc.perform(post("/api/courses/" + courseId + "/materials/" + maximum + "/summary").with(user("alice")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sections").value(6)).andExpect(jsonPath("$.modelCalls").value(7));
        assertThat(CALLS.get()).isEqualTo(7);
    }

    @Test void rejectsUnsafeProviderConfigurationAndRedactsCredentials() {
        var unsafe = new AiSettings("http://example.com/v1", "test-model", "test-secret", "max_tokens", 5, 500);
        assertThatThrownBy(unsafe::endpoint).isInstanceOf(AiFailure.class);
        assertThat(unsafe.toString()).doesNotContain("test-secret");
        assertThatThrownBy(() -> new AiSettings("https://api.openai.com/v1", "test-model", "", "max_completion_tokens", 5, 500).endpoint())
                .isInstanceOf(AiFailure.class).hasMessageContaining("api-key");
    }

    @Test void repairsMalformedOutputOnceAndCountsBothCalls() throws Exception {
        MODE.set("repair");
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.modelCalls").value(2)).andExpect(jsonPath("$.inputTokens").value(60));
        assertThat(CALLS.get()).isEqualTo(2);
    }

    @Test void refusesMalformedOrUngroundedResultsWithoutSaving() throws Exception {
        for (String mode : List.of("invalid", "ungrounded")) {
            MODE.set(mode); CALLS.set(0);
            mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isBadGateway());
            assertThat(CALLS.get()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM summaries", Integer.class)).isZero();
        }
    }

    @Test void handlesProviderTimeoutRateAndTruncatedResponses() throws Exception {
        MODE.set("timeout");
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isGatewayTimeout());
        MODE.set("rate");
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isTooManyRequests());
        MODE.set("truncated");
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isBadGateway());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM summaries", Integer.class)).isZero();
    }

    @Test void rejectsEmptyShortAndOversizedInputBeforeAnyModelCall() throws Exception {
        jdbc.update("UPDATE learning_materials SET extracted_content = ? WHERE material_id = ?", "", materialId);
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
        jdbc.update("UPDATE learning_materials SET extracted_content = ? WHERE material_id = ?", "Too short", materialId);
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
        jdbc.update("UPDATE learning_materials SET extracted_content = ? WHERE material_id = ?", ".".repeat(80), materialId);
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
        jdbc.update("UPDATE learning_materials SET extracted_content = ? WHERE material_id = ?", "a".repeat(ContentAgent.MAX_INPUT_CHARS + 1), materialId);
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isPayloadTooLarge());
        assertThat(CALLS.get()).isZero();
    }

    @Test void enforcesAuthenticationCsrfAndOwnershipWithoutSendingPrivateContent() throws Exception {
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user("bob"))).andExpect(status().isNotFound());
        assertThat(CALLS.get()).isZero();
    }

    @Test void concurrentGenerationForSameMaterialIsRejected() throws Exception {
        MODE.set("slow");
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> mvc.perform(post(path).with(user("alice")).with(csrf())).andReturn());
            assertThat(ENTERED.get().await(3, TimeUnit.SECONDS)).isTrue();
            mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isConflict());
            assertThat(first.get(4, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        } finally { executor.shutdownNow(); }
    }

    @Test void sourceChangeInvalidatesCachedResult() throws Exception {
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isOk());
        jdbc.update("UPDATE learning_materials SET extracted_content = ? WHERE material_id = ?",
                "A revised lecture explains a different part of storage design and transaction boundaries.", materialId);
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isOk());
        assertThat(CALLS.get()).isEqualTo(2);
    }

    @Test void missingUsageIsNotReportedAsZeroAndSingletonIsShared() throws Exception {
        MODE.set("no-usage");
        mvc.perform(post(path).with(user("alice")).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.inputTokens").value(-1)).andExpect(jsonPath("$.outputTokens").value(-1));
        assertThat(AIEngine.getInstance()).isSameAs(AIEngine.getInstance());
        assertThatThrownBy(() -> new AiSettings("https://api.openai.com/v1", "", "", "max_tokens", 5, 500).endpoint())
                .isInstanceOf(AiFailure.class).hasMessageContaining("Configure study.ai.model");
        assertThatThrownBy(() -> factory.createResource("Unimplemented", null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
