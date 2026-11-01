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

/** Real backend requests, with cookies and CSRF protection; no mock fallback data. */
public class CourseApi {
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
    public Course createCourse(CreateCourseRequest course) {
        var token = send(request("/api/csrf").GET().build()).headers().firstValue("X-CSRF-TOKEN")
                .orElseThrow(() -> new ApiFailure("Could not prepare a secure request. Sign in again."));
        try {
            var response = send(request("/api/courses").header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(course))).build());
            return mapper.readValue(response.body(), Course.class);
        } catch (IOException exception) {
            throw new ApiFailure("Unreadable response. Refresh your courses before retrying.");
        }
    }
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(8))
                .header("Authorization", authorization).header("Accept", "application/json");
    }
    private HttpResponse<String> send(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401) throw new ApiFailure("Username or password is incorrect. Sign in again.");
            if (status == 403) throw new ApiFailure("Your request could not be authorized. Sign in again.");
            if (status >= 400) {
                if (status == 400) throw new ApiFailure(mapper.readTree(response.body()).path("message").asText("Check your course details."));
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
