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
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chat-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ChatApiTests {
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
                    boolean refused = mode.equals("unsupported");
                    String content = JSON.writeValueAsString(Map.of("answer", "Repository interfaces separate SQL storage from application behavior.",
                            "answerable", !refused, "evidence", refused ? List.of() : List.of(mode.equals("ungrounded") ? 9999 : 1)));
                    if (mode.equals("intro-only") || mode.equals("repair-intro") && call == 1)
                        content = JSON.writeValueAsString(Map.of("answer", "To complete Stage 2 of the assignment, you should follow these steps:", "answerable", true, "evidence", List.of(1)));
                    if (mode.equals("empty-bullet")) content = JSON.writeValueAsString(Map.of("answer", "Use repository interfaces to separate storage.\n1.", "answerable", true, "evidence", List.of(1)));
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
        path = "/api/courses/" + courseId + "/chat";
    }
    private String upload(String text) throws Exception {
        var response = mvc.perform(multipart("/api/courses/" + courseId + "/materials")
                .file(new MockMultipartFile("files", "lecture.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                .with(user("alice")).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get(0).path("materialId").asText();
    }
    private org.springframework.test.web.servlet.ResultActions ask(String question) throws Exception {
        return mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", question))));
    }
    @Test void answersWithExactSourceOffsetsAndSavesHistory() throws Exception {
        var response = ask("What does the Repository pattern do?").andExpect(status().isOk())
                .andExpect(jsonPath("$.answerable").value(true)).andExpect(jsonPath("$.model").value("fixture-model"))
                .andExpect(jsonPath("$.modelCalls").value(1)).andExpect(jsonPath("$.inputTokens").value(30)).andReturn();
        var citation = json.readTree(response.getResponse().getContentAsString()).path("citations").get(0);
        String source = jdbc.queryForObject("SELECT extracted_content FROM learning_materials WHERE material_id = ?", String.class, java.util.UUID.fromString(materialId));
        assertThat(citation.path("quote").asText()).isEqualTo(source.substring(citation.path("startOffset").asInt(), citation.path("endOffset").asInt()));
        assertThat(citation.path("materialId").asText()).isEqualTo(materialId);
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].question").value("What does the Repository pattern do?"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chat_turns", Integer.class)).isEqualTo(1);
        assertThat(json.readTree(LAST_REQUEST.get()).path("messages").get(0).path("content").asText()).contains("untrusted data", "ONLY", "previous answers are not evidence");
        assertThat(json.readTree(LAST_REQUEST.get()).path("response_format").path("json_schema").path("schema").path("properties").has("answer")).isTrue();
    }
    @Test void savesSafeUnsupportedResponseAndSuppliesRecentConversationForFollowups() throws Exception {
        ask("What is a repository?").andExpect(status().isOk());
        MODE.set("unsupported");
        ask("Which year was the Repository pattern invented?").andExpect(status().isOk()).andExpect(jsonPath("$.answerable").value(false))
                .andExpect(jsonPath("$.answer").value("The available course excerpts do not contain enough information to answer this question."))
                .andExpect(jsonPath("$.citations.length()").value(0));
        var input = json.readTree(json.readTree(LAST_REQUEST.get()).path("messages").get(1).path("content").asText());
        assertThat(input.path("history").size()).isEqualTo(1);
        assertThat(input.path("history").get(0).path("question").asText()).isEqualTo("What is a repository?");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chat_turns", Integer.class)).isEqualTo(2);
    }
    @Test void unrelatedNewTopicDoesNotReuseHistoryToInventAnAnswer() throws Exception {
        ask("What is a Repository pattern?").andExpect(status().isOk());
        MODE.set("auth");
        ask("How far away is the Andromeda galaxy?").andExpect(status().isOk())
                .andExpect(jsonPath("$.answerable").value(false)).andExpect(jsonPath("$.modelCalls").value(0))
                .andExpect(jsonPath("$.model").value("course-search")).andExpect(jsonPath("$.citations.length()").value(0));
        assertThat(CALLS.get()).isEqualTo(1);
    }
    @Test void pluralVariantsStillRetrieveRelevantMaterial() throws Exception {
        ask("What are repositories?").andExpect(status().isOk()).andExpect(jsonPath("$.answerable").value(true))
                .andExpect(jsonPath("$.modelCalls").value(1));
        assertThat(CALLS.get()).isEqualTo(1);
    }
    @Test void newNamedTopicDoesNotInheritAnEarlierRefusal() throws Exception {
        upload("Enzymes are biological catalysts that lower activation energy in reactions. Their active sites interact with substrates.");
        ask("What is a Repository pattern?").andExpect(status().isOk());
        ask("How far away is the Andromeda galaxy?").andExpect(status().isOk()).andExpect(jsonPath("$.answerable").value(false));
        ask("What do enzymes do in this course?").andExpect(status().isOk());
        var input = json.readTree(json.readTree(LAST_REQUEST.get()).path("messages").get(1).path("content").asText());
        assertThat(input.path("history").isEmpty()).isTrue();
        assertThat(input.path("sources").toString()).contains("Enzymes are biological catalysts");
    }
    @Test void broadCourseQuestionsReachTheModelWithMaterialsEvenAfterARefusal() throws Exception {
        upload("Cellular respiration transfers energy from glucose into ATP. Enzymes lower activation energy in chemical reactions.");
        ask("How far away is the Andromeda galaxy?").andExpect(status().isOk()).andExpect(jsonPath("$.modelCalls").value(0));
        for (String question : List.of("Tell me about this course", "What will I learn?", "Give me an overview", "pls say something bruh")) {
            ask(question).andExpect(status().isOk()).andExpect(jsonPath("$.answerable").value(true)).andExpect(jsonPath("$.modelCalls").value(1));
            var input = json.readTree(json.readTree(LAST_REQUEST.get()).path("messages").get(1).path("content").asText());
            assertThat(input.path("history").isEmpty()).isTrue();
            assertThat(input.path("requestType").asText()).isEqualTo("course_overview");
            assertThat(input.path("sources").toString()).contains("Repository pattern", "Cellular respiration");
        }
        assertThat(CALLS.get()).isEqualTo(4);
    }
    @Test void rejectsBadQuestionsAndMissingMaterialWithoutProviderCalls() throws Exception {
        ask("   ").andExpect(status().isBadRequest());
        ask("x".repeat(1001)).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM learning_materials");
        ask("Explain repository interfaces").andExpect(status().isBadRequest());
        assertThat(CALLS.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chat_turns", Integer.class)).isZero();
    }
    @Test void protectsOwnershipAndCsrfBeforeReadingOrSendingMaterial() throws Exception {
        mvc.perform(get(path).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user("bob")).with(csrf()).contentType("application/json").content("{\"question\":\"private?\"}")).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user("alice")).contentType("application/json").content("{\"question\":\"private?\"}")).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        assertThat(CALLS.get()).isZero();
    }
    @Test void boundedRepairRejectsInventedReferencesAndPreservesPreviousHistory() throws Exception {
        ask("What is a repository?").andExpect(status().isOk());
        for (String mode : List.of("invalid", "ungrounded")) {
            MODE.set(mode); CALLS.set(0);
            ask("Explain it again").andExpect(status().isBadGateway());
            assertThat(CALLS.get()).isEqualTo(2);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chat_turns", Integer.class)).isEqualTo(1);
        MODE.set("repair"); CALLS.set(0);
        ask("Explain it again").andExpect(status().isOk()).andExpect(jsonPath("$.modelCalls").value(2));
    }
    @Test void retriesTheReportedIntroductionWithoutSavingTheIncompleteDraft() throws Exception {
        MODE.set("repair-intro");
        ask("Explain the repository pattern").andExpect(status().isOk()).andExpect(jsonPath("$.modelCalls").value(2))
                .andExpect(jsonPath("$.answer").value("Repository interfaces separate SQL storage from application behavior."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chat_turns", Integer.class)).isEqualTo(1);
        var request = json.readTree(LAST_REQUEST.get());
        assertThat(request.path("messages").get(0).path("content").asText()).contains("Your answer is incomplete", "complete replacement answer");
        assertThat(json.readTree(request.path("messages").get(1).path("content").asText()).path("previousResponse").asText()).contains("follow these steps:");
    }
    @Test void repeatedIncompleteAnswersPreserveExistingHistory() throws Exception {
        ask("What is a repository?").andExpect(status().isOk());
        for (String mode : List.of("intro-only", "empty-bullet")) {
            MODE.set(mode); CALLS.set(0);
            ask("Explain the repository pattern").andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("incomplete answer")));
            assertThat(CALLS.get()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chat_turns", Integer.class)).isEqualTo(1);
        }
    }
    @Test void providerFailuresNeverSavePartialMessagesOrExposeDiagnostics() throws Exception {
        for (String mode : List.of("auth", "rate", "timeout", "truncated")) {
            MODE.set(mode);
            int expected = mode.equals("auth") ? 503 : mode.equals("rate") ? 429 : mode.equals("timeout") ? 504 : 502;
            String response = ask("Explain the repository pattern").andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("test-provider-key", "secret-provider-diagnostic");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chat_turns", Integer.class)).isZero();
    }
    @Test void retrievalFindsRelevantTextBeyondSummaryLimitAndExcludesOtherCourses() throws Exception {
        String longMaterial = "Unrelated introductory statements for context. ".repeat(900)
                + " Mitochondria produce ATP during cellular respiration using energy from glucose. ";
        upload(longMaterial);
        var other = mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Private\",\"description\":\"Other course\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-12-20\"}"))
                .andExpect(status().isCreated()).andReturn();
        String otherId = json.readTree(other.getResponse().getContentAsString()).path("courseId").asText();
        mvc.perform(multipart("/api/courses/" + otherId + "/materials").file(new MockMultipartFile("files", "private.txt", "text/plain",
                "Mitochondria SECRET_OTHER_COURSE information must not leave this course.".getBytes(StandardCharsets.UTF_8))).with(user("alice")).with(csrf())).andExpect(status().isCreated());
        ask("How do mitochondria produce ATP from glucose?").andExpect(status().isOk());
        String request = LAST_REQUEST.get();
        assertThat(request).contains("Mitochondria produce ATP").doesNotContain("SECRET_OTHER_COURSE");
        var sources = json.readTree(json.readTree(request).path("messages").get(1).path("content").asText()).path("sources");
        assertThat(sources.size()).isLessThanOrEqualTo(6);
        assertThat(sources.get(0).path("startOffset").asInt()).isGreaterThan(32000);
    }
    @Test void courseLimitIsExplicitAndNotSilentlyTruncated() throws Exception {
        upload("Readable course content. ".repeat(25000));
        upload("Other readable course content. ".repeat(25000));
        ask("Explain course content").andExpect(status().isPayloadTooLarge());
        assertThat(CALLS.get()).isZero();
    }
    @Test void preventsConcurrentAnswersInTheSameConversation() throws Exception {
        MODE.set("slow"); var pool = Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> { ask("What is a repository?").andExpect(status().isOk()); return null; });
            assertThat(ENTERED.get().await(2, TimeUnit.SECONDS)).isTrue();
            ask("Explain it again").andExpect(status().isConflict());
            first.get(3, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chat_turns", Integer.class)).isEqualTo(1);
    }
    @Test void historyPagesAreStableChronologicalAndNeedNoModel() throws Exception {
        var id = java.util.UUID.fromString(courseId);
        for (int i = 0; i < 53; i++) jdbc.update("INSERT INTO course_chat_turns (turn_id, course_id, created_at, question, answer, answerable, citations, model, model_calls, input_tokens, output_tokens) VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, TRUE, '[]', 'history-fixture', 1, 10, 10)", java.util.UUID.randomUUID(), id, "Question " + i, "Saved historical answer " + i);
        var response = mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(50)).andReturn();
        var turns = json.readTree(response.getResponse().getContentAsString());
        assertThat(turns.get(0).path("question").asText()).isEqualTo("Question 3");
        mvc.perform(get(path + "?before=" + turns.get(0).path("sequence").asLong()).with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].question").value("Question 0"));
        mvc.perform(get(path + "?before=0").with(user("alice"))).andExpect(status().isBadRequest());
        assertThat(CALLS.get()).isZero();
    }
}

