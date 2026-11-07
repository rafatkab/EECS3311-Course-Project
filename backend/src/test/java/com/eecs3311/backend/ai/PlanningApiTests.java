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
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:planning-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PlanningApiTests {
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
                    String content = JSON.writeValueAsString(Map.of("topics", List.of(Map.of("topic", "Repository Pattern", "priority", 1, "activity", "PRACTICE", "instructions", "Compare repository interfaces with direct SQL storage in the supplied notes.", "evidence", List.of(mode.equals("ungrounded") ? 9999 : 1)))));
                    if (mode.equals("invalid") || mode.equals("repair") && call == 1) content = "not valid JSON";
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
        jdbc.update("DELETE FROM study_profiles");
        var course = mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Software Design\",\"description\":\"Patterns\",\"startDate\":\"2026-01-01\",\"endDate\":\"2035-12-31\"}"))
                .andExpect(status().isCreated()).andReturn();
        courseId = json.readTree(course.getResponse().getContentAsString()).path("courseId").asText();
        materialId = upload("The Repository pattern separates application behavior from SQL storage details. Prepared statements safely bind data to SQL queries.");
        path = "/api/courses/" + courseId + "/study-plan";
    }
    private String upload(String text) throws Exception {
        var response = mvc.perform(multipart("/api/courses/" + courseId + "/materials")
                .file(new MockMultipartFile("files", "lecture.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                .with(user("alice")).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get(0).path("materialId").asText();
    }
    private java.time.LocalDate monday() { return java.time.LocalDate.now(java.time.ZoneId.of("America/Toronto")).plusDays(7).with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY)); }
    private Map<String, Object> request(int duration, int count) { return Map.of("startDate", monday().toString(), "endDate", monday().plusDays(6).toString(), "sessionMinutes", duration, "sessionCount", count, "assessmentNotes", "I struggled with SQL coupling in a previous assessment."); }
    private void profile(int minutes) throws Exception {
        mvc.perform(put("/api/study-profile").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("availableStudyTime", minutes, "zoneId", "America/Toronto", "studyPreferences", "Practice with worked examples", "studyHabits", "Short evening sessions",
                        "availability", List.of(Map.of("day", "MONDAY", "start", "18:00", "end", "22:00")))))).andExpect(status().isOk());
    }
    private org.springframework.test.web.servlet.ResultActions generate(int duration, int count) throws Exception {
        return mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json").content(json.writeValueAsString(request(duration, count))));
    }
    private String addEvent(String type, String from, String to) throws Exception {
        var zone = java.time.ZoneId.of("America/Toronto");
        var response = mvc.perform(post("/api/courses/" + courseId + "/calendar-events").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("type", type, "title", "Test event", "startTime", monday().atTime(java.time.LocalTime.parse(from)).atZone(zone).toInstant().toString(),
                        "endTime", monday().atTime(java.time.LocalTime.parse(to)).atZone(zone).toInstant().toString())))).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).path("eventId").asText();
    }
    @Test void savesOwnedProfileAndRejectsInvalidAvailability() throws Exception {
        profile(180);
        mvc.perform(get("/api/study-profile").with(user("alice"))).andExpect(status().isOk()).andExpect(jsonPath("$.availableStudyTime").value(180));
        mvc.perform(get("/api/study-profile").with(user("bob"))).andExpect(status().isOk()).andExpect(content().string("null"));
        mvc.perform(put("/api/study-profile").with(user("alice")).with(csrf()).contentType("application/json").content("{\"availableStudyTime\":5,\"studyPreferences\":\"Read\",\"studyHabits\":\"Daily\",\"zoneId\":\"Bad/Zone\",\"availability\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/study-profile").with(user("alice"))).andExpect(jsonPath("$.availableStudyTime").value(180));
    }
    @Test void generatesSchedulesAndSynchronizesCalendarUsingObserver() throws Exception {
        profile(180);
        var response = generate(45, 4).andExpect(status().isOk()).andExpect(jsonPath("$.sessions.length()").value(4))
                .andExpect(jsonPath("$.priorityTopics[0].topic").value("Repository Pattern")).andExpect(jsonPath("$.modelCalls").value(1)).andReturn();
        String id = json.readTree(response.getResponse().getContentAsString()).path("resourceId").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events WHERE plan_id = ?", Integer.class, java.util.UUID.fromString(id))).isEqualTo(4);
        mvc.perform(get("/api/courses/" + courseId + "/planning").with(user("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.resourceId").value(id)).andExpect(jsonPath("$.plan.sessions.length()").value(4));
        var payload = json.readTree(json.readTree(LAST_REQUEST.get()).path("messages").get(1).path("content").asText());
        assertThat(payload.path("profile").path("preferences").asText()).contains("worked examples");
        assertThat(payload.path("request").path("assessmentNotes").asText()).contains("struggled");
        assertThat(json.readTree(LAST_REQUEST.get()).path("response_format").path("json_schema").path("schema").path("properties").has("topics")).isTrue();
    }
    @Test void calendarBusyTimesAndExamDeadlineConstrainTheSchedule() throws Exception {
        profile(180); addEvent("BUSY", "18:00", "19:00"); addEvent("EXAM", "20:30", "21:00");
        var response = generate(30, 3).andExpect(status().isOk()).andReturn();
        var sessions = json.readTree(response.getResponse().getContentAsString()).path("sessions");
        assertThat(java.time.Instant.parse(sessions.get(0).path("startTime").asText()).atZone(java.time.ZoneId.of("America/Toronto")).toLocalTime()).isEqualTo(java.time.LocalTime.of(19, 0));
        assertThat(java.time.Instant.parse(sessions.get(2).path("startTime").asText()).atZone(java.time.ZoneId.of("America/Toronto")).toLocalTime()).isEqualTo(java.time.LocalTime.of(20, 0));
        assertThat(json.readTree(json.readTree(LAST_REQUEST.get()).path("messages").get(1).path("content").asText()).path("importantDates").size()).isEqualTo(1);
    }
    @Test void missingProfileMaterialsAndInsufficientTimeFailBeforeModelCalls() throws Exception {
        generate(45, 4).andExpect(status().isBadRequest()); profile(30); generate(45, 4).andExpect(status().isBadRequest());
        profile(180); jdbc.update("DELETE FROM learning_materials"); generate(45, 4).andExpect(status().isBadRequest());
        assertThat(CALLS.get()).isZero();
    }
    @Test void failedRegenerationKeepsPlanAndCalendarWhileSuccessReplacesThem() throws Exception {
        profile(180);
        String initial = json.readTree(generate(45, 4).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("resourceId").asText();
        MODE.set("invalid"); CALLS.set(0); generate(45, 4).andExpect(status().isBadGateway()); assertThat(CALLS.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT resource_id FROM study_plans", String.class)).isEqualToIgnoringCase(initial);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events WHERE plan_id = ?", Integer.class, java.util.UUID.fromString(initial))).isEqualTo(4);
        MODE.set("valid");
        String replacement = json.readTree(generate(45, 4).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("resourceId").asText();
        assertThat(replacement).isNotEqualTo(initial);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events WHERE plan_id = ?", Integer.class, java.util.UUID.fromString(initial))).isZero();
    }
    @Autowired com.eecs3311.backend.planning.PlanningRepository planning;
    @Autowired com.eecs3311.backend.planning.PlanPersistence persistence;
    @Test void observerFailureRollsBackBothThePlanAndItsCalendarProjection() throws Exception {
        profile(180); generate(45, 4).andExpect(status().isOk());
        var owner = com.eecs3311.backend.course.User.authenticated("alice").userId();
        var original = planning.plan(owner, java.util.UUID.fromString(courseId)).orElseThrow();
        var replacement = new com.eecs3311.backend.planning.StudyPlan(java.util.UUID.randomUUID(), original.getCourseId(), java.time.Instant.now(), original.getRequest(), original.getProfile(), original.getPriorityTopics(), original.getSessions(), original.getModel(), 1, 10, 10);
        var calendar = new com.eecs3311.backend.planning.Calendar(owner, planning);
        replacement.attach(calendar); replacement.attach(plan -> { throw new IllegalStateException("observer-failure"); });
        assertThatThrownBy(() -> persistence.save(replacement, calendar)).hasMessageContaining("observer-failure");
        assertThat(planning.plan(owner, original.getCourseId()).orElseThrow().getResourceId()).isEqualTo(original.getResourceId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events WHERE plan_id = ?", Integer.class, original.getResourceId())).isEqualTo(4);
    }
    @Test void profileAndCalendarWritesCannotRaceWithPlanGeneration() throws Exception {
        profile(180); MODE.set("slow"); var pool = Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> { generate(45, 4).andExpect(status().isOk()); return null; });
            assertThat(ENTERED.get().await(2, TimeUnit.SECONDS)).isTrue();
            generate(45, 4).andExpect(status().isConflict());
            mvc.perform(post("/api/courses/" + courseId + "/calendar-events").with(user("alice")).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isConflict());
            first.get(3, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
    @Test void ownershipAuthenticationAndCsrfProtectEveryPlanningWrite() throws Exception {
        profile(180);
        mvc.perform(post(path).with(user("bob")).with(csrf()).contentType("application/json").content(json.writeValueAsString(request(45, 4)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/" + courseId + "/planning").with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user("alice")).contentType("application/json").content(json.writeValueAsString(request(45, 4)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/study-profile")).andExpect(status().isUnauthorized());
        assertThat(CALLS.get()).isZero();
    }
    @Test void invalidModelReferencesAndTimeoutNeverSaveAPlan() throws Exception {
        profile(180);
        for (String mode : List.of("ungrounded", "rate", "timeout")) {
            MODE.set(mode); generate(45, 4).andExpect(status().is(mode.equals("rate") ? 429 : mode.equals("timeout") ? 504 : 502));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_plans", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events", Integer.class)).isZero();
    }
    @Test void manualEventsAreOwnedAndGeneratedSessionsCannotBeDeletedIndividually() throws Exception {
        profile(180); String event = addEvent("BUSY", "12:00", "13:00");
        mvc.perform(delete("/api/courses/" + courseId + "/calendar-events/" + event).with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/courses/" + courseId + "/calendar-events/" + event).with(user("alice")).with(csrf())).andExpect(status().isOk());
        generate(45, 4).andExpect(status().isOk());
        String session = jdbc.queryForObject("SELECT event_id FROM calendar_events LIMIT 1", String.class);
        mvc.perform(delete("/api/courses/" + courseId + "/calendar-events/" + session).with(user("alice")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void otherCoursePlansConsumeTheSameStudentsBudgetAndAvoidExistingSessions() throws Exception {
        profile(180); generate(60, 2).andExpect(status().isOk());
        var response = mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Second course\",\"description\":\"Other course\",\"startDate\":\"2026-01-01\",\"endDate\":\"2035-12-31\"}"))
                .andExpect(status().isCreated()).andReturn();
        courseId = json.readTree(response.getResponse().getContentAsString()).path("courseId").asText();
        path = "/api/courses/" + courseId + "/study-plan";
        upload("Repository interfaces separate storage from application behavior in this second course.");
        int before = CALLS.get(); generate(60, 2).andExpect(status().isBadRequest()); assertThat(CALLS.get()).isEqualTo(before);
        var generated = generate(60, 1).andExpect(status().isOk()).andReturn();
        var start = java.time.Instant.parse(json.readTree(generated.getResponse().getContentAsString()).path("sessions").get(0).path("startTime").asText());
        assertThat(start.atZone(java.time.ZoneId.of("America/Toronto")).toLocalTime()).isEqualTo(java.time.LocalTime.of(20, 0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events", Integer.class)).isEqualTo(3);
    }
    @Test void badPlanDatesAndDurationsReturnUsefulErrorsBeforeCallingTheModel() throws Exception {
        profile(180);
        mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("startDate", monday().toString(), "endDate", monday().plusDays(1).toString(), "sessionMinutes", 16, "sessionCount", 2))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("15-minute increments")));
        var invalid = new java.util.HashMap<>(request(45, 4)); invalid.put("startDate", java.time.LocalDate.now().minusDays(1).toString()); invalid.put("endDate", java.time.LocalDate.now().plusDays(5).toString());
        mvc.perform(post(path).with(user("alice")).with(csrf()).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        assertThat(CALLS.get()).isZero();
    }
}
