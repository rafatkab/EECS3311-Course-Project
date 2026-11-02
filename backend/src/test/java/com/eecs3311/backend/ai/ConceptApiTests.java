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
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concept-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ConceptApiTests {
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
                    String name = mode.equals("paraphrase") ? "Persistence Abstraction" : "Repository Pattern";
                    int evidenceId = mode.equals("ungrounded") || (mode.equals("repair-id") && call == 1) ? 9999 : 1;
                    if (mode.equals("late-topic")) {
                        var input = JSON.readTree(body.path("messages").get(1).path("content").asText());
                        for (var option : input.path("evidenceOptions")) {
                            if (option.path("quote").asText().contains("Cellular respiration")) evidenceId = option.path("id").asInt();
                        }
                        name = "Energy Production";
                    }
                    var concept = Map.of("name", name, "description", "Separates application behavior from SQL storage details.", "evidence", mode.equals("repeat-evidence") ? List.of(1, 1) : List.of(evidenceId));
                    Object concepts = mode.equals("duplicates") ? List.of(concept, Map.of("name", " repository-pattern ", "description", "Another explanation of the same concept.", "evidence", List.of(1))) : List.of(concept);
                    String content = JSON.writeValueAsString(Map.of("concepts", concepts));
                    if (mode.equals("empty")) content = "{\"concepts\":[]}";
                    if (mode.equals("wrong-types")) content = "{\"concepts\":[{\"name\":false,\"description\":42,\"evidence\":[1]}]}";
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
        path = "/api/courses/" + courseId + "/materials/" + materialId + "/concepts";
    }
    private String upload(String text) throws Exception {
        var response = mvc.perform(multipart("/api/courses/" + courseId + "/materials")
                .file(new MockMultipartFile("files", "lecture.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                .with(user("alice")).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get(0).path("materialId").asText();
    }

    private org.springframework.test.web.servlet.ResultActions extract() throws Exception {
        return mvc.perform(post(path).with(user("alice")).with(csrf()));
    }
    @Test void generatesAndPersistsConceptsThroughRealHttpAdapter() throws Exception {
        extract().andExpect(status().isOk()).andExpect(jsonPath("$.concepts[0].name").value("Repository Pattern"))
                .andExpect(jsonPath("$.concepts[0].description").isString())
                .andExpect(jsonPath("$.concepts[0].evidence[0]").value("The Repository pattern separates application behavior from SQL storage details."))
                .andExpect(jsonPath("$.model").value("fixture-model")).andExpect(jsonPath("$.modelCalls").value(1))
                .andExpect(jsonPath("$.inputTokens").value(30)).andExpect(jsonPath("$.sourceHash").doesNotExist());
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM concept_sets", Integer.class)).isEqualTo(1);
        var request = json.readTree(LAST_REQUEST.get());
        assertThat(request.path("response_format").path("json_schema").path("schema").path("required").get(0).asText()).isEqualTo("concepts");
        assertThat(request.path("messages").get(0).path("content").asText()).contains("untrusted source data");
    }
    @Test void cachesAndPreservesPreviousResultOnFailedRegeneration() throws Exception {
        String initial = json.readTree(extract().andReturn().getResponse().getContentAsString()).path("resourceId").asText();
        MODE.set("invalid");
        extract().andExpect(status().isOk()).andExpect(jsonPath("$.resourceId").value(initial));
        assertThat(CALLS.get()).isEqualTo(1);
        mvc.perform(post(path + "?regenerate=true").with(user("alice")).with(csrf())).andExpect(status().isBadGateway());
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.resourceId").value(initial));
        MODE.set("valid");
        var regenerated = mvc.perform(post(path + "?regenerate=true").with(user("alice")).with(csrf())).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(regenerated.getResponse().getContentAsString()).path("resourceId").asText()).isNotEqualTo(initial);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM concept_sets", Integer.class)).isEqualTo(1);
    }
    @Test void deduplicatesNormalizedNamesAndRepairsMalformedOutputOnce() throws Exception {
        MODE.set("duplicates");
        extract().andExpect(status().isOk()).andExpect(jsonPath("$.concepts.length()").value(1));
        jdbc.update("DELETE FROM concept_sets"); MODE.set("repair"); CALLS.set(0);
        extract().andExpect(status().isOk()).andExpect(jsonPath("$.modelCalls").value(2));
    }
    @Test void acceptsParaphrasedTopicNamesAndDeduplicatesRepeatedReferences() throws Exception {
        for (String mode : List.of("paraphrase", "repeat-evidence")) {
            jdbc.update("DELETE FROM concept_sets"); MODE.set(mode);
            extract().andExpect(status().isOk()).andExpect(jsonPath("$.concepts.length()").value(1))
                    .andExpect(jsonPath("$.concepts[0].name").value(mode.equals("paraphrase") ? "Persistence Abstraction" : "Repository Pattern"))
                    .andExpect(jsonPath("$.concepts[0].evidence.length()").value(1))
                    .andExpect(jsonPath("$.concepts[0].evidence[0]").value("The Repository pattern separates application behavior from SQL storage details."));
        }
        assertThat(CALLS.get()).isEqualTo(2);
    }
    @Test void includesUnsampledTopicsAndTheEndOfLongSentencesInEvidenceOptions() throws Exception {
        String source = java.util.stream.IntStream.range(0, 35).mapToObj(i ->
                i == 17 ? "Background information about biological processes and energy requirements ".repeat(4)
                        + "Cellular respiration converts glucose into ATP for cellular work."
                        : "Background sentence number " + i + " provides additional context.")
                .collect(java.util.stream.Collectors.joining("\n"));
        materialId = upload(source);
        path = "/api/courses/" + courseId + "/materials/" + materialId + "/concepts";
        MODE.set("late-topic");
        var result = extract().andExpect(status().isOk()).andExpect(jsonPath("$.concepts[0].name").value("Energy Production")).andReturn();
        String quote = json.readTree(result.getResponse().getContentAsString()).path("concepts").get(0).path("evidence").get(0).asText();
        assertThat(quote).contains("Cellular respiration");
        assertThat(source).contains(quote);
        var options = json.readTree(json.readTree(LAST_REQUEST.get()).path("messages").get(1).path("content").asText()).path("evidenceOptions");
        assertThat(options.size()).isGreaterThan(12);
    }
    @Test void repairIncludesPreviousOutputAndTheSpecificValidationFailure() throws Exception {
        MODE.set("repair-id");
        extract().andExpect(status().isOk()).andExpect(jsonPath("$.modelCalls").value(2));
        var request = json.readTree(LAST_REQUEST.get());
        assertThat(request.path("messages").get(0).path("content").asText()).contains("Evidence must use integer IDs", "untrusted data");
        assertThat(json.readTree(request.path("messages").get(1).path("content").asText()).path("previousResponse").asText()).contains("9999");
    }
    @Test void groupsDenseBulletListsWithoutDroppingTheirContents() throws Exception {
        String source = java.util.stream.IntStream.range(0, 180).mapToObj(i -> "- Definition " + i + " explains a course topic.")
                .collect(java.util.stream.Collectors.joining("\n"));
        // Keep this a single source section so the request contains the original evidence options.
        source = source.substring(0, source.lastIndexOf('\n', 5500));
        materialId = upload(source);
        path = "/api/courses/" + courseId + "/materials/" + materialId + "/concepts";
        extract().andExpect(status().isOk());
        var options = json.readTree(json.readTree(LAST_REQUEST.get()).path("messages").get(1).path("content").asText()).path("evidenceOptions");
        assertThat(options.size()).isLessThanOrEqualTo(48);
        var recovered = new StringBuilder();
        for (var option : options) {
            String quote = option.path("quote").asText();
            assertThat(source).contains(quote);
            assertThat(quote.length()).isLessThanOrEqualTo(300);
            recovered.append(quote).append(' ');
        }
        assertThat(recovered.toString().replaceAll("\\s+", "")).isEqualTo(source.replaceAll("\\s+", ""));
    }
    @Test void rejectsMalformedEmptyWrongTypeAndInventedEvidenceWithoutSaving() throws Exception {
        for (String mode : List.of("invalid", "empty", "wrong-types", "ungrounded")) {
            MODE.set(mode); CALLS.set(0);
            extract().andExpect(status().isBadGateway());
            assertThat(CALLS.get()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM concept_sets", Integer.class)).isZero();
        }
        assertThat(mvc.perform(post(path).with(user("alice")).with(csrf())).andReturn().getResponse().getContentAsString())
                .contains("Evidence must use integer IDs", "No new concepts were saved");
    }
    @Test void rejectsInsufficientAndOversizedSourceBeforeProviderAccess() throws Exception {
        for (String text : List.of("Short note.", "Readable sentence about software design. ".repeat(1000))) {
            String id = upload(text);
            mvc.perform(post("/api/courses/" + courseId + "/materials/" + id + "/concepts").with(user("alice")).with(csrf()))
                    .andExpect(status().is(text.length() < 40 ? 400 : 413));
        }
        assertThat(CALLS.get()).isZero();
    }
    @Test void sectionsAndConsolidatesAcrossTheDocumentWithBoundedUsage() throws Exception {
        String id = upload("Repository Pattern interfaces separate application logic from storage.\n".repeat(110));
        path = "/api/courses/" + courseId + "/materials/" + id + "/concepts";
        extract().andExpect(status().isOk()).andExpect(jsonPath("$.sections").value(2))
                .andExpect(jsonPath("$.modelCalls").value(3)).andExpect(jsonPath("$.inputTokens").value(90));
        assertThat(json.readTree(LAST_REQUEST.get()).path("messages").get(0).path("content").asText()).contains("Consolidate ALL");
    }
    @Test void protectsOwnershipAuthenticationAndCsrfBeforeSendingPrivateContent() throws Exception {
        mvc.perform(post(path).with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isNotFound());
        assertThat(CALLS.get()).isZero();
    }
    @Test void handlesProviderErrorsAndTimeoutWithoutLeakingDiagnostics() throws Exception {
        for (String mode : List.of("auth", "rate", "truncated", "timeout")) {
            MODE.set(mode);
            int expected = mode.equals("auth") ? 503 : mode.equals("rate") ? 429 : mode.equals("timeout") ? 504 : 502;
            var response = extract().andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("secret-provider-diagnostic", "test-provider-key");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM concept_sets", Integer.class)).isZero();
    }
    @Test void preventsConcurrentRegenerationAndInvalidatesCacheWhenSourceChanges() throws Exception {
        MODE.set("slow");
        var pool = Executors.newSingleThreadExecutor();
        try {
            var future = pool.submit(() -> { extract().andExpect(status().isOk()); return null; });
            assertThat(ENTERED.get().await(2, TimeUnit.SECONDS)).isTrue();
            extract().andExpect(status().isConflict());
            future.get(3, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        jdbc.update("UPDATE learning_materials SET extracted_content = ? WHERE material_id = ?", "The Repository Pattern isolates data storage. Prepared statements safely bind query inputs.", java.util.UUID.fromString(materialId));
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isNotFound());
        MODE.set("valid");
        extract().andExpect(status().isOk());
        assertThat(CALLS.get()).isEqualTo(2);
    }
}

