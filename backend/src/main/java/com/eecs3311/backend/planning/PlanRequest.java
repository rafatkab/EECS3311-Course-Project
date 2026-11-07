package com.eecs3311.backend.planning;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record PlanRequest(LocalDate startDate, LocalDate endDate, int sessionMinutes, int sessionCount, String assessmentNotes) {
    public PlanRequest {
        if (startDate == null || endDate == null || endDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, endDate) > 55)
            throw new IllegalArgumentException("Choose a study period of 1 to 56 days.");
        if (sessionMinutes < 15 || sessionMinutes > 180 || sessionMinutes % 15 != 0 || sessionCount < 1 || sessionCount > 30)
            throw new IllegalArgumentException("Choose 1 to 30 sessions, each 15 to 180 minutes in 15-minute increments.");
        assessmentNotes = assessmentNotes == null ? "" : assessmentNotes.strip();
        if (assessmentNotes.length() > 2000) throw new IllegalArgumentException("Assessment notes allow up to 2000 characters.");
    }
}
