package com.eecs3311.course;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayOutputStream;

/** Real backend requests, with cookies and CSRF protection; no mock fallback data. */
public class CourseApi {
    public FlashcardData.Deck generateFlashcards(UUID course, FlashcardData.Request input) {
        try {
            String token; synchronized (this) { token = csrfToken(); }
            return mapper.readValue(send(request("/api/courses/" + course + "/flashcards").timeout(Duration.ofSeconds(200))
                    .header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(input))).build()).body(), FlashcardData.Deck.class);
        } catch (IOException e) { throw new ApiFailure("Could not read the generated deck. Refresh saved decks before retrying."); }
    }
    public List<FlashcardData.Saved> flashcardDecks(UUID course, Long before) {
        try { return mapper.readValue(send(request("/api/courses/" + course + "/flashcards" + (before == null ? "" : "?before=" + before)).GET().build()).body(), new TypeReference<>() { }); }
        catch (IOException e) { throw new ApiFailure("Could not read saved flashcard decks."); }
    }
    public FlashcardData.Deck flashcardDeck(UUID course, UUID deck) {
        try { return mapper.readValue(send(request("/api/courses/" + course + "/flashcards/" + deck).GET().build()).body(), FlashcardData.Deck.class); }
        catch (IOException e) { throw new ApiFailure("Could not read the saved flashcard deck."); }
    }
    private final String baseUrl;
    private final String authorization;
    private final ObjectMapper mapper;
    private final HttpClient http;
    public CourseApi(String baseUrl, String username, String password, ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.authorization = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.mapper = mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();
    }
    public List<Course> viewCourses() {
        try {
            return mapper.readValue(send(request("/api/courses").GET().build()).body(), new TypeReference<>() { });
        } catch (IOException exception) {
            throw new ApiFailure("The backend returned an unreadable response.");
        }
    }
    public PlanningData.State planningState(UUID course) {
        try { return mapper.readValue(send(request("/api/courses/" + course + "/planning").GET().build()).body(), PlanningData.State.class); }
        catch (IOException exception) { throw new ApiFailure("Could not read study planning data."); }
    }
    private void planningWrite(String path, Object body, String method) {
        try {
            String token; synchronized (this) { token = csrfToken(); }
            send(request(path).timeout(Duration.ofSeconds(250)).header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build());
        } catch (IOException exception) { throw new ApiFailure("Could not prepare study planning request. Refresh saved data before retrying."); }
    }
    public void saveStudyProfile(PlanningData.Profile profile) { planningWrite("/api/study-profile", profile, "PUT"); }
    public void generateStudyPlan(UUID course, PlanningData.Request request) { planningWrite("/api/courses/" + course + "/study-plan", request, "POST"); }
    public void addCalendarEvent(UUID course, PlanningData.EventInput event) { planningWrite("/api/courses/" + course + "/calendar-events", event, "POST"); }
    public void removeCalendarEvent(UUID course, UUID event) { planningWrite("/api/courses/" + course + "/calendar-events/" + event, null, "DELETE"); }
    public ChatTurn askQuestion(UUID courseId, String question) {
        try {
            String token;
            synchronized (this) { token = csrfToken(); }
            return mapper.readValue(send(request("/api/courses/" + courseId + "/chat").timeout(Duration.ofSeconds(250))
                    .header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(java.util.Map.of("question", question)))).build()).body(), ChatTurn.class);
        } catch (IOException exception) { throw new ApiFailure("Could not read the answer. Refresh chat history before retrying."); }
    }
    public List<ChatTurn> chatHistory(UUID courseId, Long before) {
        try {
            return mapper.readValue(send(request("/api/courses/" + courseId + "/chat" + (before == null ? "" : "?before=" + before)).GET().build()).body(), new TypeReference<>() { });
        } catch (IOException exception) { throw new ApiFailure("Could not read the course chat history."); }
    }
    public synchronized Course createCourse(CreateCourseRequest course) {
        var token = csrfToken();
        try {
            var response = send(request("/api/courses").header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(course))).build());
            return mapper.readValue(response.body(), Course.class);
        } catch (IOException exception) {
            throw new ApiFailure("Unreadable response. Refresh your courses before retrying.");
        }
    }
    private String csrfToken() {
        return send(request("/api/csrf").GET().build()).headers().firstValue("X-CSRF-TOKEN")
                .orElseThrow(() -> new ApiFailure("Could not prepare a secure request. Sign in again."));
    }

    public List<LearningMaterial> listMaterials(UUID courseId) {
        try {
            return mapper.readValue(send(request("/api/courses/" + courseId + "/materials").GET().build()).body(), new TypeReference<>() { });
        } catch (IOException exception) {
            throw new ApiFailure("The backend returned an unreadable material list.");
        }
    }

    public String materialContent(UUID courseId, UUID materialId) {
        return send(request("/api/courses/" + courseId + "/materials/" + materialId + "/content")
                .setHeader("Accept", "text/plain, application/json").GET().build()).body();
    }

    public byte[] downloadMaterial(UUID courseId, UUID materialId) {
        return send(request("/api/courses/" + courseId + "/materials/" + materialId + "/download")
                .setHeader("Accept", "application/octet-stream, application/json").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    public Summary summarize(UUID courseId, UUID materialId, boolean regenerate) {
        try {
            String token;
            synchronized (this) { token = csrfToken(); }
            var response = send(request("/api/courses/" + courseId + "/materials/" + materialId + "/summary?regenerate=" + regenerate)
                    .timeout(Duration.ofSeconds(200)).header("X-CSRF-TOKEN", token)
                    .POST(HttpRequest.BodyPublishers.noBody()).build());
            return mapper.readValue(response.body(), Summary.class);
        } catch (IOException exception) { throw new ApiFailure("Could not read the summary response. Try View saved summary before generating again."); }
    }

    public Summary viewSummary(UUID courseId, UUID materialId) {
        try {
            return mapper.readValue(send(request("/api/courses/" + courseId + "/materials/" + materialId + "/summary").GET().build()).body(), Summary.class);
        } catch (IOException exception) { throw new ApiFailure("Could not read the saved summary."); }
    }

    public ConceptSet extractConcepts(UUID courseId, UUID materialId, boolean regenerate) {
        try {
            String token;
            synchronized (this) { token = csrfToken(); }
            var response = send(request("/api/courses/" + courseId + "/materials/" + materialId + "/concepts?regenerate=" + regenerate)
                    .timeout(Duration.ofSeconds(200)).header("X-CSRF-TOKEN", token)
                    .POST(HttpRequest.BodyPublishers.noBody()).build());
            return mapper.readValue(response.body(), ConceptSet.class);
        } catch (IOException exception) { throw new ApiFailure("Could not read the concepts response. Try View saved concepts before extracting again."); }
    }

    public ConceptSet viewConcepts(UUID courseId, UUID materialId) {
        try {
            return mapper.readValue(send(request("/api/courses/" + courseId + "/materials/" + materialId + "/concepts").GET().build()).body(), ConceptSet.class);
        } catch (IOException exception) { throw new ApiFailure("Could not read the saved concepts."); }
    }

    // Keep token acquisition and POST together: concurrent first uploads must not
    // create different sessions and overwrite each other's CSRF cookie.
    public synchronized void uploadMaterial(UUID courseId, String filename, byte[] bytes) {
        if (bytes.length > 10 * 1024 * 1024) throw new ApiFailure("Each file must be 10 MiB or smaller.");
        String boundary = "StudyPlanner" + UUID.randomUUID().toString().replace("-", "");
        String safeName = filename.replace('\\', '_').replace('"', '_').replace('\r', '_').replace('\n', '_');
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + safeName
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            send(request("/api/courses/" + courseId + "/materials").timeout(Duration.ofSeconds(45))
                    .header("X-CSRF-TOKEN", csrfToken()).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
        } catch (IOException exception) {
            throw new ApiFailure("Could not prepare the file for upload.");
        }
    }
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(8))
                .header("Authorization", authorization).header("Accept", "application/json");
    }
    private HttpResponse<String> send(HttpRequest request) {
        return send(request, HttpResponse.BodyHandlers.ofString());
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            var response = http.send(request, handler);
            int status = response.statusCode();
            if (status == 401) throw new ApiFailure("Username or password is incorrect. Sign in again.");
            if (status == 403) throw new ApiFailure("Your request could not be authorized. Sign in again.");
            if (status >= 400) {
                if (status == 400 || status == 404 || status == 409 || status == 413 || status == 415 || status == 429 || status == 502 || status == 503 || status == 504) {
                    String body = response.body() instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(response.body());
                    var details = mapper.readTree(body);
                    throw new ApiFailure(details == null ? "The file or course could not be processed."
                            : details.path("message").asText("The file or course could not be processed."));
                }
                throw new ApiFailure("The backend could not complete the request. Try again shortly.");
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiFailure("Request interrupted. Refresh your courses before retrying.");
        } catch (IOException exception) {
            throw new ApiFailure("Cannot reach the backend. Check that it is running; refresh your courses before retrying.");
        }
    }
    public static class ApiFailure extends RuntimeException {
        public ApiFailure(String message) { super(message); }
    }
}
