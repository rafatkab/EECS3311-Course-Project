package com.eecs3311.backend.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class QuizRepository {
    public record Saved(UUID resourceId, long sequence, Instant createdAt, int questionCount, String difficulty, String topics) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public QuizRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (java.io.IOException e) { throw new IllegalStateException("Could not encode quiz data.", e); }
    }
    public void save(Quiz quiz) {
        jdbc.update("INSERT INTO quizzes(resource_id,course_id,created_at,question_count,difficulty,topics,quiz_json) VALUES(?,?,?,?,?,?,?)",
                quiz.getResourceId(), quiz.getCourseId(), Timestamp.from(quiz.getCreatedAt()), quiz.getRequest().questionCount(), quiz.getRequest().difficulty().name(), quiz.getRequest().topics(), encode(quiz));
    }
    public Optional<Quiz> find(UUID course, UUID quiz) {
        return jdbc.query("SELECT quiz_json FROM quizzes WHERE course_id=? AND resource_id=?", (row, i) -> {
            try { return json.readValue(row.getString(1), Quiz.class); } catch (java.io.IOException e) { throw new java.sql.SQLException("Could not read quiz.", e); }
        }, course, quiz).stream().findFirst();
    }
    public List<Saved> list(UUID course, Long before) {
        return jdbc.query("SELECT resource_id,sequence,created_at,question_count,difficulty,topics FROM quizzes WHERE course_id=? AND sequence<? ORDER BY sequence DESC LIMIT 25",
                (r,i) -> new Saved(r.getObject(1, UUID.class), r.getLong(2), r.getTimestamp(3).toInstant(), r.getInt(4), r.getString(5), r.getString(6)), course, before == null ? Long.MAX_VALUE : before);
    }
    public QuizAttempt createAttempt(UUID quiz) {
        var attempt = new QuizAttempt(UUID.randomUUID(), quiz, Instant.now(), null, 0, Map.of());
        jdbc.update("INSERT INTO quiz_attempts(attempt_id,quiz_id,created_at,version,responses_json) VALUES(?,?,?,?,?)",
                attempt.attemptId(), quiz, Timestamp.from(attempt.createdAt()), 0, encode(attempt.responses()));
        return attempt;
    }
    public Optional<QuizAttempt> attempt(UUID quiz, UUID attempt) {
        return jdbc.query("SELECT * FROM quiz_attempts WHERE quiz_id=? AND attempt_id=?", this::readAttempt, quiz, attempt).stream().findFirst();
    }
    public List<QuizAttempt> attempts(UUID quiz) {
        return jdbc.query("SELECT * FROM quiz_attempts WHERE quiz_id=? ORDER BY sequence DESC LIMIT 25", this::readAttempt, quiz);
    }
    private QuizAttempt readAttempt(java.sql.ResultSet r, int index) throws java.sql.SQLException {
        try {
            var submitted = r.getTimestamp("submitted_at");
            return new QuizAttempt(r.getObject("attempt_id", UUID.class), r.getObject("quiz_id", UUID.class), r.getTimestamp("created_at").toInstant(),
                    submitted == null ? null : submitted.toInstant(), r.getInt("version"), json.readValue(r.getString("responses_json"), new com.fasterxml.jackson.core.type.TypeReference<Map<UUID,String>>() { }));
        } catch (java.io.IOException e) { throw new java.sql.SQLException("Could not read quiz attempt.", e); }
    }
    public boolean update(QuizAttempt attempt) {
        return jdbc.update("UPDATE quiz_attempts SET responses_json=?,submitted_at=?,version=version+1 WHERE attempt_id=? AND quiz_id=? AND version=? AND submitted_at IS NULL",
                encode(attempt.responses()), attempt.submittedAt() == null ? null : Timestamp.from(attempt.submittedAt()), attempt.attemptId(), attempt.quizId(), attempt.version() - 1) == 1;
    }
}
