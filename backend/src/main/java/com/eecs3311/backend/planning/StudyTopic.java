package com.eecs3311.backend.planning;

import java.util.List;
import java.util.UUID;

public record StudyTopic(String topic, int priority, String activity, String instructions, List<Citation> citations) {
    public record Citation(UUID materialId, String fileName, String quote) { }
    public StudyTopic { citations = List.copyOf(citations); }
}
