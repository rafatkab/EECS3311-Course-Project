package com.eecs3311.backend.course;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService service;
    public CourseController(CourseService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Course> createCourse(Principal principal, @Valid @RequestBody CreateCourseRequest request) {
        Course course = service.createCourse(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/courses/" + course.courseId())).body(course);
    }

    @GetMapping
    public List<Course> viewCourses(Principal principal) { return service.viewCourses(principal.getName()); }

    @GetMapping("/{id}")
    public Course getCourse(Principal principal, @PathVariable UUID id) {
        return service.getCourse(principal.getName(), id);
    }
}
