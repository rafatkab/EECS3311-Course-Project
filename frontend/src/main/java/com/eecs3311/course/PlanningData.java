package com.eecs3311.course;

import java.time.*;
import java.util.List;
import java.util.UUID;

public final class PlanningData {
    private PlanningData() { }
    public record Availability(DayOfWeek day, LocalTime start, LocalTime end) { }
    public record Profile(int availableStudyTime, String studyPreferences, String studyHabits, String zoneId, List<Availability> availability) { }
    public record Request(LocalDate startDate, LocalDate endDate, int sessionMinutes, int sessionCount, String assessmentNotes) { }
    public record Citation(UUID materialId, String fileName, String quote) { }
    public record Topic(String topic, int priority, String activity, String instructions, List<Citation> citations) { }
    public record Session(UUID sessionId, String topic, String activityType, String instructions, Instant startTime, int duration, List<Citation> citations) { }
    public record Plan(UUID resourceId, UUID courseId, Instant createdAt, Request request, Profile profile,
                       List<Topic> priorityTopics, List<Session> sessions, String model, int modelCalls, long inputTokens, long outputTokens) { }
    public record Event(UUID eventId, UUID courseId, UUID planId, String type, String title, Instant startTime, Instant endTime) { }
    public record EventInput(String type, String title, Instant startTime, Instant endTime) { }
    public record State(Profile profile, Plan plan, List<Event> events) { }
}
