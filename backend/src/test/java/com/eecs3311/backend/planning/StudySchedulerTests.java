package com.eecs3311.backend.planning;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StudySchedulerTests {
    private final UUID owner = UUID.randomUUID(), course = UUID.randomUUID();
    private final StudyScheduler scheduler = new StudyScheduler();
    private StudyProfile profile(int minutes, DayOfWeek day, LocalTime from, LocalTime to) {
        return new StudyProfile(owner, minutes, "Practice with examples", "Short focused sessions", "America/Toronto", List.of(new StudyProfile.Availability(day, from, to)));
    }
    private Instant time(String local) { return LocalDateTime.parse(local).atZone(ZoneId.of("America/Toronto")).toInstant(); }
    @Test void skipsBusyTimesAndFinishesBeforeAnExam() {
        var events = List.of(new CalendarEvent(UUID.randomUUID(), owner, course, null, "BUSY", "Work", time("2027-01-04T18:00"), time("2027-01-04T19:00")),
                new CalendarEvent(UUID.randomUUID(), owner, course, null, "EXAM", "Exam", time("2027-01-04T20:30"), time("2027-01-04T21:00")));
        var request = new PlanRequest(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10), 30, 3, "");
        assertThat(scheduler.allocate(course, profile(180, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0)), request, events, time("2027-01-01T00:00")))
                .containsExactly(time("2027-01-04T19:00"), time("2027-01-04T19:30"), time("2027-01-04T20:00"));
    }
    @Test void countsOtherCoursesAgainstWeeklyBudgetButReplacesOwnPlan() {
        var own = new CalendarEvent(UUID.randomUUID(), owner, course, UUID.randomUUID(), "STUDY", "Old session", time("2027-01-04T18:00"), time("2027-01-04T19:00"));
        var other = new CalendarEvent(UUID.randomUUID(), owner, UUID.randomUUID(), UUID.randomUUID(), "STUDY", "Other course", time("2027-01-04T20:00"), time("2027-01-04T21:00"));
        var request = new PlanRequest(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10), 60, 1, "");
        var profile = profile(120, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));
        assertThat(scheduler.allocate(course, profile, request, List.of(own, other), time("2027-01-01T00:00"))).containsExactly(time("2027-01-04T18:00"));
        assertThatThrownBy(() -> scheduler.allocate(course, profile, new PlanRequest(request.startDate(), request.endDate(), 60, 2, ""), List.of(other), time("2027-01-01T00:00"))).hasMessageContaining("Not enough");
    }
    @Test void weeklyBudgetResetsAndNoSessionStartsInThePast() {
        var request = new PlanRequest(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 11), 30, 2, "");
        assertThat(scheduler.allocate(course, profile(30, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0)), request, List.of(), time("2027-01-04T18:01")))
                .containsExactly(time("2027-01-04T18:15"), time("2027-01-11T18:00"));
    }
    @Test void daylightSavingFallbackUsesRealDurationsAndBudget() {
        var request = new PlanRequest(LocalDate.of(2027, 11, 7), LocalDate.of(2027, 11, 7), 60, 2, "");
        var starts = scheduler.allocate(course, profile(120, DayOfWeek.SUNDAY, LocalTime.of(1, 0), LocalTime.of(3, 0)), request, List.of(), Instant.parse("2027-11-01T00:00:00Z"));
        assertThat(starts).containsExactly(Instant.parse("2027-11-07T05:00:00Z"), Instant.parse("2027-11-07T06:00:00Z"));
    }
    @Test void anEarlierExamTodayDoesNotBlockFutureStudySessions() {
        var pastExam = new CalendarEvent(UUID.randomUUID(), owner, course, null, "EXAM", "Completed exam", time("2027-01-04T09:00"), time("2027-01-04T10:00"));
        var request = new PlanRequest(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 4), 30, 1, "");
        assertThat(scheduler.allocate(course, profile(60, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0)), request, List.of(pastExam), time("2027-01-04T18:01")))
                .containsExactly(time("2027-01-04T18:15"));
    }
    @Test void profileAndRequestRejectContradictoryInput() {
        assertThatThrownBy(() -> profile(300, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0))).hasMessageContaining("Weekly study minutes");
        assertThatThrownBy(() -> new StudyProfile(owner, 60, "Practice", "Regular", "America/Toronto", List.of(
                new StudyProfile.Availability(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0)),
                new StudyProfile.Availability(DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(21, 0))))).hasMessageContaining("overlap");
        assertThatThrownBy(() -> new PlanRequest(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 4, 1), 45, 4, "")).hasMessageContaining("56 days");
    }
}
