package com.eecs3311.backend.course;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Keeps domain/application logic independent of the database technology. */
public interface CourseRepository {
    Course save(Course course);
    List<Course> findByOwner(UUID ownerId);
    Optional<Course> findByIdAndOwner(UUID courseId, UUID ownerId);
}
