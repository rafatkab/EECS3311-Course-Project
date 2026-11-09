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
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quiz-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class QuizApiTests {
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
                    var input = JSON.readTree(body.path("messages").get(1).path("content").asText());
                    String fact = input.path("targetFact").asText();
                    String answer = mode.equals("duplicate") ? "The Repository pattern" : java.util.Arrays.stream(fact.split("\\s+")).limit(3).collect(java.util.stream.Collectors.joining(" "));
                    if (mode.equals("repeat-term")) answer = "SQL";
                    int offset = mode.equals("duplicate") ? 0 : input.path("alreadyAskedQuestions").size();
                    boolean multiple = input.path("questionType").asText().equals("MULTIPLE_CHOICE");
                    String content = JSON.writeValueAsString(Map.of("answer", answer, "distractors", multiple
                            ? mode.equals("bad-distractors") ? List.of(answer, answer, answer) : List.of("Storage must always be embedded in the user interface.", "Prepared statements require concatenating user input.", "Every application must share one global SQL connection.") : List.of()));
                    if (mode.equals("invalid") || mode.equals("bad-prefix") && call <= 6 || mode.equals("repair") && call == 1) content = "not valid JSON";
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
    private static int sourceId(com.fasterxml.jackson.databind.JsonNode input, String answer) {
        for (var source : input.path("sources")) if (source.path("quote").asText().toLowerCase().contains(answer.toLowerCase())) return source.path("id").asInt();
        return 1;
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
        jdbc.update("DELETE FROM study_profiles");
        var course = mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Software Design\",\"description\":\"Patterns\",\"startDate\":\"2026-01-01\",\"endDate\":\"2035-12-31\"}"))
                .andExpect(status().isCreated()).andReturn();
        courseId = json.readTree(course.getResponse().getContentAsString()).path("courseId").asText();
        materialId = upload("The Repository pattern separates application behavior from SQL storage details. " + java.util.stream.IntStream.rangeClosed(1, 24).mapToObj(i -> "Repository fact " + i + " keeps SQL storage separate for example " + i + ".").collect(java.util.stream.Collectors.joining(" ")));
        path = "/api/courses/" + courseId + "/quizzes";
    }
    private String upload(String text) throws Exception {
        var response = mvc.perform(multipart("/api/courses/" + courseId + "/materials")
                .file(new MockMultipartFile("files", "lecture.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                .with(user("alice")).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get(0).path("materialId").asText();
    }
    private org.springframework.test.web.servlet.ResultActions generate(int limit) throws Exception {
        return mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("questionCount", limit, "difficulty", "HARD", "questionTypes", List.of("SHORT_ANSWER", "MULTIPLE_CHOICE"), "materialIds", List.of(materialId), "topics", "Repository"))));
    }
    private com.fasterxml.jackson.databind.JsonNode quiz(int count) throws Exception { return json.readTree(generate(count).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()); }
    private com.fasterxml.jackson.databind.JsonNode start(String quiz) throws Exception {
        return json.readTree(mvc.perform(post(path+"/"+quiz+"/attempts").with(user("alice")).with(csrf())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String attemptPath(com.fasterxml.jackson.databind.JsonNode state) { return path+"/"+state.path("quiz").path("resourceId").asText()+"/attempts/"+state.path("attempt").path("attemptId").asText(); }
    @Test void exactCountTypesDifficultyAndNoAnswerKeyLeak() throws Exception {
        var result=quiz(4); assertThat(result.path("questions").size()).isEqualTo(4);
        assertThat(result.toString()).doesNotContain("expectedAnswer","citations","targetAnswer");
        assertThat(result.path("questions").get(0).path("choices").size()).isZero();
        assertThat(result.path("questions").get(1).path("choices").size()).isEqualTo(4);
        assertThat(CALLS.get()).isEqualTo(4);
        var input=json.readTree(json.readTree(LAST_REQUEST.get()).path("messages").get(1).path("content").asText());
        assertThat(input.path("difficulty").asText()).isEqualTo("HARD"); assertThat(input.path("topics").asText()).isEqualTo("Repository");
    }
    @Test void invalidCountsAndMaterialsFailBeforeModel() throws Exception {
        generate(0).andExpect(status().isBadRequest()); generate(21).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json").content("{\"questionCount\":2,\"difficulty\":\"EASY\",\"materialIds\":[\""+java.util.UUID.randomUUID()+"\"]}" )).andExpect(status().isNotFound());
        assertThat(CALLS.get()).isZero();
    }
    @Test void insufficientFactsAndUnmatchedTopicsDoNotCallModel() throws Exception {
        jdbc.update("DELETE FROM learning_materials"); materialId=upload("Repository interfaces keep SQL storage separate from application behavior.");
        generate(3).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json").content("{\"questionCount\":1,\"difficulty\":\"EASY\",\"topics\":\"Photosynthesis\"}" )).andExpect(status().isBadRequest());
        assertThat(CALLS.get()).isZero();
    }
    @Test void savedQuizzesWorkOfflineAndAreOwned() throws Exception {
        String id=quiz(2).path("resourceId").asText(); MODE.set("auth");
        mvc.perform(get(path+"/"+id).with(user("alice"))).andExpect(status().isOk()).andExpect(jsonPath("$.questions.length()").value(2));
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$[0].questionCount").value(2));
        mvc.perform(get(path+"/"+id).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(post(path+"/"+id+"/attempts").with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        assertThat(CALLS.get()).isEqualTo(2);
    }
    @Test void csrfProtectsQuizAndAttemptWrites() throws Exception {
        mvc.perform(post(path).with(user("alice")).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        String id=quiz(1).path("resourceId").asText();
        mvc.perform(post(path+"/"+id+"/attempts").with(user("alice"))).andExpect(status().isForbidden());
        var state=start(id); mvc.perform(put(attemptPath(state)).with(user("alice")).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
    }
    @Test void draftResumeDoesNotLeakKeyAndRejectsStaleVersions() throws Exception {
        var quiz=quiz(2); var state=start(quiz.path("resourceId").asText()); assertThat(state.path("answerKey").isEmpty()).isTrue();
        String question=quiz.path("questions").get(0).path("questionId").asText();
        String body=json.writeValueAsString(Map.of("version",0,"responses",Map.of(question,"My draft answer")));
        mvc.perform(put(attemptPath(state)).with(user("alice")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.attempt.version").value(1)).andExpect(jsonPath("$.answerKey.length()").value(0));
        mvc.perform(put(attemptPath(state)).with(user("alice")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get(attemptPath(state)).with(user("alice"))).andExpect(jsonPath("$.attempt.responses['"+question+"']").value("My draft answer"));
    }
    @Test void finishRequiresAllAnswersThenRevealsKeyAndLocksResponses() throws Exception {
        var quiz=quiz(2); var state=start(quiz.path("resourceId").asText()); String url=attemptPath(state);
        mvc.perform(post(url+"/submit").with(user("alice")).with(csrf()).contentType("application/json").content("{\"version\":0,\"responses\":{}}" )).andExpect(status().isBadRequest());
        var answers=new java.util.HashMap<String,String>();
        for(var q:quiz.path("questions")) answers.put(q.path("questionId").asText(),q.path("choices").isEmpty()?"My answer":q.path("choices").get(0).asText());
        String body=json.writeValueAsString(Map.of("version",0,"responses",answers));
        var finished=mvc.perform(post(url+"/submit").with(user("alice")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.answerKey.length()").value(2)).andExpect(jsonPath("$.attempt.version").value(1)).andReturn();
        for(var key:json.readTree(finished.getResponse().getContentAsString()).path("answerKey")) {
            String question=key.path("question").asText(), answer=key.path("expectedAnswer").asText();
            String restored=question.substring(question.indexOf('\n')+1).replace("____",answer);
            assertThat(key.path("citations").get(0).path("quote").asText().replaceAll("\\s+"," ")).contains(restored);
            if(!key.path("choices").isEmpty()) assertThat(key.path("choices")).filteredOn(choice -> choice.asText().equals(answer)).hasSize(1);
        }
        mvc.perform(put(url).with(user("alice")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get(url).with(user("bob"))).andExpect(status().isNotFound());
    }
    @Test void rejectsUnknownQuestionOversizedTextAndUnlistedChoice() throws Exception {
        var quiz=quiz(2); var state=start(quiz.path("resourceId").asText());
        for(var responses:List.of(Map.of(java.util.UUID.randomUUID().toString(),"unknown"),Map.of(quiz.path("questions").get(0).path("questionId").asText(),"x".repeat(4001)),Map.of(quiz.path("questions").get(1).path("questionId").asText(),"not a listed choice")))
            mvc.perform(put(attemptPath(state)).with(user("alice")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",0,"responses",responses)))).andExpect(status().isBadRequest());
        mvc.perform(get(attemptPath(state)).with(user("alice"))).andExpect(jsonPath("$.attempt.version").value(0));
    }
    @Test void repairsOnceAndNeverPersistsAnIncompleteQuiz() throws Exception {
        MODE.set("repair"); quiz(1); assertThat(CALLS.get()).isEqualTo(2);
        MODE.set("invalid"); generate(2).andExpect(status().isBadGateway());
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void duplicateQuestionsAndInvalidDistractorsAreRejected() throws Exception {
        MODE.set("duplicate"); generate(2).andExpect(status().isBadGateway());
        MODE.set("bad-distractors"); generate(2).andExpect(status().isBadGateway());
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void providerErrorsNeverSaveQuizzes() throws Exception {
        for(String mode:List.of("auth","rate","timeout","truncated")) {
            MODE.set(mode); generate(1).andExpect(status().is(mode.equals("auth")?503:mode.equals("rate")?429:mode.equals("timeout")?504:502));
        }
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void concurrentGenerationReturnsConflict() throws Exception {
        MODE.set("slow"); var executor=Executors.newSingleThreadExecutor();
        try { var first=executor.submit(() -> { try { quiz(1); } catch(Exception e) { throw new RuntimeException(e); } });
            assertThat(ENTERED.get().await(3,TimeUnit.SECONDS)).isTrue(); generate(1).andExpect(status().isConflict()); first.get(5,TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
    @Test void attemptIdsCannotBeUsedWithAnotherQuiz() throws Exception {
        String first=quiz(1).path("resourceId").asText(),second=quiz(1).path("resourceId").asText();var state=start(first);
        mvc.perform(get(path+"/"+second+"/attempts/"+state.path("attempt").path("attemptId").asText()).with(user("alice"))).andExpect(status().isNotFound());
    }
    @Test void omittedTypesDefaultToShortAnswer() throws Exception {
        mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json").content("{\"questionCount\":1,\"difficulty\":\"EASY\"}" ))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.questions[0].type").value("SHORT_ANSWER"));
    }
    @Test void continuesPastThreeFailedFactsWhenLaterFactsAreUsable() throws Exception {
        MODE.set("bad-prefix"); quiz(2); assertThat(CALLS.get()).isEqualTo(8);
    }
    @Test void sameCorrectTermCanAppearInDifferentQuestions() throws Exception {
        MODE.set("repeat-term"); var result=quiz(3); assertThat(result.path("questions").size()).isEqualTo(3);
        assertThat(CALLS.get()).isEqualTo(3);
    }
    @Test void selectionDoesNotSendOtherMaterialsToTheModel() throws Exception {
        upload("PRIVATE_UNSELECTED_SECTION: Photosynthesis uses light energy to convert carbon dioxide and water into sugars.");
        quiz(1); assertThat(LAST_REQUEST.get()).doesNotContain("PRIVATE_UNSELECTED_SECTION");
    }
    @Test void historyUsesStablePagesOfTwentyFive() throws Exception {
        String id=quiz(1).path("resourceId").asText();
        for(int i=0;i<26;i++) jdbc.update("INSERT INTO quizzes(resource_id,course_id,created_at,question_count,difficulty,topics,quiz_json) SELECT ?,course_id,created_at,question_count,difficulty,topics,quiz_json FROM quizzes WHERE resource_id=?",java.util.UUID.randomUUID(),java.util.UUID.fromString(id));
        var page=json.readTree(mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.size()).isEqualTo(25);
        mvc.perform(get(path+"?before="+page.get(24).path("sequence").asLong()).with(user("alice"))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(path+"?before=0").with(user("alice"))).andExpect(status().isBadRequest());
    }
}
