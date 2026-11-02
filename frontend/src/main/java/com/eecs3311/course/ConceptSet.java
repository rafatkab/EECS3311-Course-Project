package com.eecs3311.course;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConceptSet(UUID resourceId, UUID courseId, UUID materialId, Instant createdAt,
                         List<Concept> concepts, String model, int sourceCharacters,
                         int sections, int modelCalls, long inputTokens, long outputTokens) {
    public record Concept(String name, String description, List<String> evidence) { }
}
