package com.eecs3311.backend.planning;

import java.time.*;
import java.util.*;

/** Saved student preferences; availableStudyTime is a weekly budget in minutes. */
public record StudyProfile(UUID ownerId, int availableStudyTime, String studyPreferences, String studyHabits,
                           String zoneId, List<Availability> availability) {
    public record Availability(DayOfWeek day, LocalTime start, LocalTime end) {
        public Availability {
            if (day == null || start == null || end == null || !end.isAfter(start)
                    || start.getSecond() != 0 || end.getSecond() != 0 || start.getNano() != 0 || end.getNano() != 0)
                throw new IllegalArgumentException("Availability needs a day and minute-precision start/end times on the same day.");
        }
    }
    public StudyProfile {
        if (ownerId == null || zoneId == null) throw new IllegalArgumentException("A study profile needs an owner and time zone.");
        try { ZoneId.of(zoneId); } catch (DateTimeException exception) { throw new IllegalArgumentException("Use a valid time zone, such as America/Toronto."); }
        studyPreferences = studyPreferences == null ? "" : studyPreferences.strip();
        studyHabits = studyHabits == null ? "" : studyHabits.strip();
        if (studyPreferences.isEmpty() || studyPreferences.length() > 1000 || studyHabits.isEmpty() || studyHabits.length() > 1000)
            throw new IllegalArgumentException("Preferences and study habits are required, up to 1000 characters each.");
        if (availability == null || availability.isEmpty() || availability.size() > 28)
            throw new IllegalArgumentException("Provide 1 to 28 weekly availability windows.");
        availability = availability.stream().sorted(Comparator.comparing(Availability::day).thenComparing(Availability::start)).toList();
        long capacity = 0;
        Availability previous = null;
        for (var window : availability) {
            if (previous != null && previous.day() == window.day() && previous.end().isAfter(window.start()))
                throw new IllegalArgumentException("Availability windows on the same day cannot overlap.");
            capacity += Duration.between(window.start(), window.end()).toMinutes(); previous = window;
        }
        if (availableStudyTime < 15 || availableStudyTime > capacity)
            throw new IllegalArgumentException("Weekly study minutes must be at least 15 and fit within the availability windows.");
    }
    public StudyProfile updateAvailability(int minutes, String timeZone, List<Availability> windows) {
        return new StudyProfile(ownerId, minutes, studyPreferences, studyHabits, timeZone, windows);
    }
    public StudyProfile updatePreferences(String preferences, String habits) {
        return new StudyProfile(ownerId, availableStudyTime, preferences, habits, zoneId, availability);
    }
}
