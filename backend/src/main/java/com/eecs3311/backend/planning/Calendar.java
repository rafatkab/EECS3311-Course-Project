package com.eecs3311.backend.planning;

import java.time.Instant;
import java.util.*;

/** Stage 1 concrete observer; updates the student's stored calendar when a plan changes. */
public final class Calendar implements ScheduleObserver {
    private final UUID owner;
    private final PlanningRepository repository;
    public Calendar(UUID owner, PlanningRepository repository) { this.owner = owner; this.repository = repository; }
    public List<CalendarEvent> viewEvents(Instant from, Instant to) { return repository.events(owner, from, to); }
    public List<CalendarEvent> planningEvents(StudyProfile profile, PlanRequest request) {
        var zone = java.time.ZoneId.of(profile.zoneId());
        var from = request.startDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
        var to = request.endDate().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
        return viewEvents(from, to);
    }
    public void addEvent(CalendarEvent event) {
        if (!owner.equals(event.ownerId())) throw new IllegalArgumentException("Calendar event ownership does not match.");
        repository.addEvent(event);
    }
    public boolean removeEvent(UUID course, UUID event) { return repository.removeManualEvent(owner, course, event); }
    @Override public void update(StudyPlan plan) {
        if (!owner.equals(plan.getProfile().ownerId())) throw new IllegalArgumentException("Study plan ownership does not match.");
        repository.replaceStudySessions(owner, plan);
    }
}
