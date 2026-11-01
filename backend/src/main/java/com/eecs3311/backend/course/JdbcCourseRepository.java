package com.eecs3311.backend.course;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCourseRepository implements CourseRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<Course> mapper = (row, index) -> new Course(
            row.getObject("course_id", UUID.class), row.getObject("owner_id", UUID.class),
            row.getString("name"), row.getString("description"),
            row.getObject("start_date", LocalDate.class), row.getObject("end_date", LocalDate.class),
            row.getTimestamp("created_at").toInstant());

    public JdbcCourseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Course save(Course course) {
        jdbc.update("INSERT INTO courses (course_id, owner_id, name, description, start_date, end_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                course.courseId(), course.ownerId(), course.name(), course.description(),
                course.startDate(), course.endDate(), Timestamp.from(course.createdAt()));
        return course;
    }

    @Override
    public List<Course> findByOwner(UUID ownerId) {
        return jdbc.query("SELECT * FROM courses WHERE owner_id = ? ORDER BY created_at DESC, course_id", mapper, ownerId);
    }

    @Override
    public Optional<Course> findByIdAndOwner(UUID courseId, UUID ownerId) {
        return jdbc.query("SELECT * FROM courses WHERE course_id = ? AND owner_id = ?", mapper, courseId, ownerId)
                .stream().findFirst();
    }
}
