package com.eecs3311.backend.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** JDBC storage for the planning aggregate and its calendar projection. */
@Repository
public class PlanningRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PlanningRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot encode planning data.", exception); }
    }
    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot read planning data.", exception); }
    }
    public Optional<StudyProfile> profile(UUID owner) {
        return jdbc.query("SELECT profile_json FROM study_profiles WHERE owner_id = ?", (row, index) -> decode(row.getString(1), StudyProfile.class), owner).stream().findFirst();
    }
    public void saveProfile(StudyProfile profile) { jdbc.update("MERGE INTO study_profiles (owner_id, profile_json) KEY(owner_id) VALUES (?, ?)", profile.ownerId(), encode(profile)); }
    public Optional<StudyPlan> plan(UUID owner, UUID course) {
        return jdbc.query("SELECT plan_json FROM study_plans WHERE owner_id = ? AND course_id = ?", (row, index) -> decode(row.getString(1), StudyPlan.class), owner, course).stream().findFirst();
    }
    public void savePlan(StudyPlan plan) {
        UUID owner = plan.getProfile().ownerId();
        // Remove the previous projection before changing the latest plan's primary key.
        jdbc.update("DELETE FROM calendar_events WHERE owner_id = ? AND course_id = ? AND type = 'STUDY'", owner, plan.getCourseId());
        jdbc.update("MERGE INTO study_plans (resource_id, owner_id, course_id, plan_json) KEY(course_id) VALUES (?, ?, ?, ?)",
                plan.getResourceId(), owner, plan.getCourseId(), encode(plan));
    }
    public List<CalendarEvent> events(UUID owner, Instant from, Instant to) {
        return jdbc.query("SELECT * FROM calendar_events WHERE owner_id = ? AND start_time < ? AND end_time > ? ORDER BY start_time, event_id", (row, index) ->
                new CalendarEvent(row.getObject("event_id", UUID.class), owner, row.getObject("course_id", UUID.class), row.getObject("plan_id", UUID.class),
                        row.getString("type"), row.getString("title"), row.getTimestamp("start_time").toInstant(), row.getTimestamp("end_time").toInstant()),
                owner, Timestamp.from(to), Timestamp.from(from));
    }
    public void addEvent(CalendarEvent event) {
        jdbc.update("INSERT INTO calendar_events (event_id, owner_id, course_id, plan_id, type, title, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                event.eventId(), event.ownerId(), event.courseId(), event.planId(), event.type(), event.title(), Timestamp.from(event.startTime()), Timestamp.from(event.endTime()));
    }
    public boolean removeManualEvent(UUID owner, UUID course, UUID event) {
        return jdbc.update("DELETE FROM calendar_events WHERE event_id = ? AND owner_id = ? AND course_id = ? AND plan_id IS NULL", event, owner, course) == 1;
    }
    public void replaceStudySessions(UUID owner, StudyPlan plan) {
        jdbc.update("DELETE FROM calendar_events WHERE owner_id = ? AND course_id = ? AND type = 'STUDY'", owner, plan.getCourseId());
        for (var session : plan.getSessions()) addEvent(new CalendarEvent(session.sessionId(), owner, plan.getCourseId(), plan.getResourceId(),
                "STUDY", session.topic(), session.startTime(), session.endTime()));
    }
}
