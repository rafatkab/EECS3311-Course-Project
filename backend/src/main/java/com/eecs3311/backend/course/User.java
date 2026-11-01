package com.eecs3311.backend.course;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Stage 1 User: creates owned courses and retrieves them through persistence. */
public record User(UUID userId, String name) {
    public static User authenticated(String username) {
        return new User(UUID.nameUUIDFromBytes(("student:" + username).getBytes(StandardCharsets.UTF_8)), username);
    }

    public Course createCourse(String name, String description, LocalDate start, LocalDate end) {
        return new Course(UUID.randomUUID(), userId, name, description, start, end, Instant.now());
    }

    public List<Course> viewCourses(CourseRepository repository) {
        return repository.findByOwner(userId);
    }
}
