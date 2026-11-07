package com.eecs3311.backend.planning;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudySession(UUID sessionId, String topic, String activityType, String instructions,
                           Instant startTime, int duration, List<StudyTopic.Citation> citations) {
    public StudySession { citations = List.copyOf(citations); }
    public Instant endTime() { return startTime.plusSeconds(duration * 60L); }
}
