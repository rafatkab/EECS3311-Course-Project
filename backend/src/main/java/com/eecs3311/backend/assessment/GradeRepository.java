package com.eecs3311.backend.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class GradeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public GradeRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public void save(GradeResult grade) {
        try { jdbc.update("INSERT INTO quiz_grades(attempt_id,quiz_id,grade_id,graded_at,grade_json) VALUES(?,?,?,?,?)",grade.attemptId(),grade.quizId(),grade.gradeId(),Timestamp.from(grade.gradedAt()),json.writeValueAsString(grade)); }
        catch(java.io.IOException e) { throw new IllegalStateException("Could not encode grade result.",e); }
    }
    public Optional<GradeResult> find(UUID quiz,UUID attempt) {
        return jdbc.query("SELECT grade_json FROM quiz_grades WHERE quiz_id=? AND attempt_id=?",this::read,quiz,attempt).stream().findFirst();
    }
    public List<GradeResult> recentForCourse(UUID course) {
        return jdbc.query("SELECT g.grade_json FROM quiz_grades g JOIN quizzes q ON q.resource_id=g.quiz_id JOIN quiz_attempts a ON a.attempt_id=g.attempt_id WHERE q.course_id=? AND NOT EXISTS (SELECT 1 FROM quiz_grades newer JOIN quiz_attempts na ON na.attempt_id=newer.attempt_id WHERE newer.quiz_id=g.quiz_id AND na.sequence>a.sequence) ORDER BY a.sequence DESC LIMIT 5",this::read,course);
    }
    private GradeResult read(java.sql.ResultSet r,int i) throws java.sql.SQLException {
        try { return json.readValue(r.getString(1),GradeResult.class); } catch(java.io.IOException e) { throw new java.sql.SQLException("Could not read grade result.",e); }
    }
}
