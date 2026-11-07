package com.eecs3311.backend.planning;

import com.eecs3311.backend.ai.AiFailure;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Deterministic allocation; the LLM never chooses dates, durations, or availability. */
@Component
public class StudyScheduler {
    public List<Instant> allocate(UUID courseId, StudyProfile profile, PlanRequest request, List<CalendarEvent> events, Instant now) {
        ZoneId zone = ZoneId.of(profile.zoneId());
        var busy = events.stream().filter(event -> !(event.type().equals("STUDY") && event.courseId().equals(courseId))).toList();
        Instant deadline = events.stream().filter(event -> event.courseId().equals(courseId) && Set.of("EXAM", "QUIZ").contains(event.type())
                && event.startTime().isAfter(now)
                && !event.startTime().isBefore(request.startDate().atStartOfDay(zone).toInstant())
                && event.startTime().isBefore(request.endDate().plusDays(1).atStartOfDay(zone).toInstant()))
                .map(CalendarEvent::startTime).min(Comparator.naturalOrder()).orElse(Instant.MAX);
        var used = new HashMap<LocalDate, Long>();
        for (var event : busy) if (event.type().equals("STUDY"))
            used.merge(week(event.startTime(), zone), Duration.between(event.startTime(), event.endTime()).toMinutes(), Long::sum);
        var result = new ArrayList<Instant>();
        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            for (var window : profile.availability()) if (window.day() == date.getDayOfWeek()) {
                Instant end = date.atTime(window.end()).atZone(zone).toInstant();
                for (Instant start = date.atTime(window.start()).atZone(zone).toInstant(); !start.plusSeconds(request.sessionMinutes() * 60L).isAfter(end); start = start.plusSeconds(15 * 60)) {
                    Instant finish = start.plusSeconds(request.sessionMinutes() * 60L);
                    if (start.isBefore(now) || finish.isAfter(deadline)) continue;
                    LocalDate week = week(start, zone);
                    if (used.getOrDefault(week, 0L) + request.sessionMinutes() > profile.availableStudyTime()) continue;
                    Instant candidate = start;
                    if (busy.stream().anyMatch(event -> candidate.isBefore(event.endTime()) && finish.isAfter(event.startTime()))) continue;
                    if (!result.isEmpty() && start.isBefore(result.get(result.size() - 1).plusSeconds(request.sessionMinutes() * 60L))) continue;
                    result.add(start); used.merge(week, (long) request.sessionMinutes(), Long::sum);
                    if (result.size() == request.sessionCount()) return List.copyOf(result);
                }
            }
        }
        throw new AiFailure(400, "Not enough available study time before the deadline. Reduce sessions or duration, extend the dates, or update availability/calendar events.");
    }
    private LocalDate week(Instant time, ZoneId zone) { return time.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
}
