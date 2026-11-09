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
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flashcard-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FlashcardApiTests {
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
                    int limit = input.path("batchLimit").asInt();
                    int offset = input.path("alreadyAskedQuestions").size();
                    var cards = new java.util.ArrayList<Object>();
                    for (int i = 0; i < (mode.equals("fewer") ? 1 : mode.equals("empty") ? 0 : limit); i++) {
                        int number = mode.equals("duplicates") ? 0 : mode.equals("skip-fact") ? call * 100 + i : offset + i;
                        cards.add(Map.of("question", (mode.equals("statement") || mode.equals("mixed") && call == 1 && i > 0 || mode.equals("late-invalid") && call > 1 || mode.equals("skip-fact") && input.path("answerOptions").get(i).asText().contains("fact 1 ")) ? "This is a statement, not a question." : "What does Repository pattern number " + number + " separate?", "answer", mode.equals("invented") ? "Protein synthesis." : mode.equals("short") ? "SQL" : input.path("answerOptions").get(i).asText(), "evidence", List.of(mode.equals("ungrounded") ? 9999 : mode.equals("wrong-valid") ? 2 : sourceId(input, mode.equals("short") ? "SQL" : input.path("answerOptions").get(i).asText()))));
                    }
                    String content = JSON.writeValueAsString(Map.of("cards", cards));
                    if (mode.equals("invalid") || mode.equals("bad-multi") && limit > 1 || mode.equals("repair") && call == 1) content = "not valid JSON";
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
        materialId = upload("The Repository pattern separates application behavior from SQL storage details. " + java.util.stream.IntStream.rangeClosed(1, 24).mapToObj(i -> "Repository fact " + i + " keeps SQL storage separate.").collect(java.util.stream.Collectors.joining(" ")));
        path = "/api/courses/" + courseId + "/flashcards";
    }
    private String upload(String text) throws Exception {
        var response = mvc.perform(multipart("/api/courses/" + courseId + "/materials")
                .file(new MockMultipartFile("files", "lecture.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                .with(user("alice")).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get(0).path("materialId").asText();
    }
    private org.springframework.test.web.servlet.ResultActions generate(int limit) throws Exception {
        return mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("cardLimit", limit, "materialIds", List.of(materialId), "focus", "Repository"))));
    }
    @Test void batchesGroundedCardsAndReadsWithoutProvider() throws Exception {
        var result = generate(5).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(5)).andReturn();
        var deck = json.readTree(result.getResponse().getContentAsString());
        assertThat(CALLS.get()).isEqualTo(2);
        assertThat(deck.path("modelCalls").asInt()).isEqualTo(2);
        assertThat(deck.path("inputTokens").asInt()).isEqualTo(60);
        assertThat(deck.path("cards").get(0).path("citations").get(0).path("materialId").asText()).isEqualTo(materialId);
        MODE.set("auth");
        mvc.perform(get(path + "/" + deck.path("resourceId").asText()).with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cards.length()").value(5));
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].cardCount").value(5));
        assertThat(CALLS.get()).isEqualTo(2);
    }
    @Test void invalidLimitsRejectedBeforeCallingProvider() throws Exception {
        for (int limit : new int[]{0, -1, 21}) generate(limit).andExpect(status().isBadRequest());
        assertThat(CALLS.get()).isZero();
    }
    @Test void ownershipAndCsrfAreRequired() throws Exception {
        mvc.perform(post(path).with(user("alice")).contentType("application/json").content("{\"cardLimit\":5}" )).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("bob")).with(csrf()).contentType("application/json").content("{\"cardLimit\":5}" )).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user("bob"))).andExpect(status().isNotFound());
        var deck = json.readTree(generate(1).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(get(path + "/" + deck.path("resourceId").asText()).with(user("bob"))).andExpect(status().isNotFound());
    }
    @Test void foreignMaterialRejectedWithoutProvider() throws Exception {
        mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json")
                .content("{\"cardLimit\":5,\"materialIds\":[\"" + java.util.UUID.randomUUID() + "\"]}" )).andExpect(status().isNotFound());
        assertThat(CALLS.get()).isZero();
    }
    @Test void unmatchedFocusDoesNotCallProvider() throws Exception {
        mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json")
                .content("{\"cardLimit\":5,\"focus\":\"photosynthesis\"}" )).andExpect(status().isBadRequest());
        assertThat(CALLS.get()).isZero();
    }
    @Test void continuesAfterShortBatchesToReachTenCards() throws Exception {
        MODE.set("fewer"); generate(10).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(10)); assertThat(CALLS.get()).isEqualTo(10);
    }
    @Test void acceptsShortFactualAnswers() throws Exception {
        MODE.set("short"); generate(1).andExpect(status().isCreated()).andExpect(jsonPath("$.cards[0].answer").value("SQL"));
    }
    @Test void selectedMaterialsExcludeOtherCourseTextFromModelContext() throws Exception {
        upload("PRIVATE_UNSELECTED_SECTION: Osmosis moves water through a selectively permeable membrane.");
        generate(2).andExpect(status().isCreated());
        assertThat(LAST_REQUEST.get()).doesNotContain("PRIVATE_UNSELECTED_SECTION").contains("Repository pattern separates");
    }
    @Test void supportsTwentyCardsInBoundedBatches() throws Exception {
        generate(20).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(20));
        assertThat(CALLS.get()).isEqualTo(7);
    }
    @Test void twentyOneCardBatchesAreNotStoppedByTheOldFourteenCallLimit() throws Exception {
        MODE.set("fewer"); generate(20).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(20));
        assertThat(CALLS.get()).isEqualTo(20);
    }
    @Test void invalidMultiCardBatchesFallBackToSingleCardsAndReachTen() throws Exception {
        MODE.set("bad-multi"); generate(10).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(10));
        assertThat(CALLS.get()).isEqualTo(12);
    }
    @Test void actuallyExhaustedFactsProduceASmallerDeckWithoutPadding() throws Exception {
        jdbc.update("DELETE FROM learning_materials");
        materialId = upload("Repository interfaces separate behavior from storage. Repository queries use prepared statements to bind values safely.");
        generate(10).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(2));
        assertThat(CALLS.get()).isEqualTo(1);
    }
    @Test void resolvesAnIncorrectValidCitationToThePassageContainingTheAnswer() throws Exception {
        MODE.set("wrong-valid");
        var deck = json.readTree(generate(1).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var card = deck.path("cards").get(0);
        assertThat(card.path("citations").get(0).path("quote").asText()).contains(card.path("answer").asText());
        assertThat(CALLS.get()).isEqualTo(1);
    }
    @Test void removesRepeatedQuestionsAndStopsWhenNoNewCards() throws Exception {
        MODE.set("duplicates"); generate(5).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(1)); assertThat(CALLS.get()).isEqualTo(3);
    }
    @Test void repairsMalformedOutputOnce() throws Exception {
        MODE.set("repair"); generate(1).andExpect(status().isCreated()); assertThat(CALLS.get()).isEqualTo(2);
        assertThat(LAST_REQUEST.get()).contains("previousResponse", "validationError");
    }
    @Test void keepsValidCardsFromAMixedBatchAndContinues() throws Exception {
        MODE.set("mixed");
        generate(3).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(3))
                .andExpect(jsonPath("$.cards[0].question").value("What does Repository pattern number 0 separate?"));
        assertThat(CALLS.get()).isEqualTo(2);
    }
    @Test void ignoresUnfinishedHeadingsAndJoinsPdfStyleWrappedSentences() throws Exception {
        jdbc.update("DELETE FROM learning_materials");
        materialId = upload("Repository facts:\n\nRepository interfaces keep application\nbehavior separate from storage details.");
        generate(1).andExpect(status().isCreated()).andExpect(jsonPath("$.cards[0].answer")
                .value("Repository interfaces keep application behavior separate from storage details."));
    }
    @Test void skipsAnUnusableFactInsteadOfAbortingTheDeck() throws Exception {
        MODE.set("skip-fact");
        var deck = json.readTree(generate(4).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(deck.path("cards").size()).isEqualTo(4);
        assertThat(deck.path("cards")).allMatch(card -> !card.path("answer").asText().contains("fact 1 "));
    }
    @Test void laterInvalidOutputSavesOnlyValidatedCardsAndRetainsEarlierDecks() throws Exception {
        generate(1).andExpect(status().isCreated()); CALLS.set(0); MODE.set("late-invalid");
        generate(10).andExpect(status().isCreated()).andExpect(jsonPath("$.cards.length()").value(3));
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(2));
    }
    @Test void rejectsBadEvidenceAndKeepsExistingDeck() throws Exception {
        generate(1).andExpect(status().isCreated()); MODE.set("ungrounded"); generate(1).andExpect(status().isBadGateway());
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void emptyOutputDoesNotSaveDeck() throws Exception {
        MODE.set("empty"); generate(3).andExpect(status().isBadRequest()); mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void rejectsInventedAnswersEvenWithValidSourceIds() throws Exception {
        MODE.set("invented"); generate(1).andExpect(status().isBadGateway());
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(0));
        assertThat(CALLS.get()).isEqualTo(6);
    }
    @Test void rejectsStatementsInsteadOfQuestions() throws Exception {
        MODE.set("statement"); generate(1).andExpect(status().isBadGateway());
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void providerErrorsAndTruncationDoNotSaveDecks() throws Exception {
        MODE.set("auth"); generate(1).andExpect(status().isServiceUnavailable());
        MODE.set("truncated"); generate(1).andExpect(status().isBadGateway());
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void rejectsConcurrentGeneration() throws Exception {
        MODE.set("slow");
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> { try { generate(1).andExpect(status().isCreated()); } catch (Exception e) { throw new RuntimeException(e); } });
            assertThat(ENTERED.get().await(3, TimeUnit.SECONDS)).isTrue();
            generate(1).andExpect(status().isConflict()); first.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
    @Test void historyPaginationIsStable() throws Exception {
        var initial = json.readTree(generate(1).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String original = initial.path("resourceId").asText();
        for (int i = 0; i < 26; i++) jdbc.update("INSERT INTO flashcard_decks (resource_id, course_id, created_at, card_limit, card_count, focus, deck_json) SELECT ?, course_id, created_at, card_limit, card_count, focus, deck_json FROM flashcard_decks WHERE resource_id = ?", java.util.UUID.randomUUID(), java.util.UUID.fromString(original));
        var page = json.readTree(mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.size()).isEqualTo(25);
        mvc.perform(get(path + "?before=" + page.get(24).path("sequence").asLong()).with(user("alice"))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(path + "?before=0").with(user("alice"))).andExpect(status().isBadRequest());
    }
}
