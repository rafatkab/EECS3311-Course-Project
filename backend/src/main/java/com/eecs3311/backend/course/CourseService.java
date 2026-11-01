package com.eecs3311.backend.course;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
public class CourseService {
    private final CourseRepository repository;

    public CourseService(CourseRepository repository) { this.repository = repository; }

    @Transactional
    public Course createCourse(String username, CreateCourseRequest request) {
        Course course = User.authenticated(username).createCourse(
                request.name(), request.description(), request.startDate(), request.endDate());
        return repository.save(course);
    }

    public List<Course> viewCourses(String username) {
        return User.authenticated(username).viewCourses(repository);
    }

    public Course getCourse(String username, UUID id) {
        return repository.findByIdAndOwner(id, User.authenticated(username).userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
    }
}
