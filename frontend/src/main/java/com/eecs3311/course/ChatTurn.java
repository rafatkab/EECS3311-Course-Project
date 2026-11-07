package com.eecs3311.course;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatTurn(UUID turnId, long sequence, UUID courseId, Instant createdAt, String question, String answer,
                       boolean answerable, List<Citation> citations, String model, int modelCalls, long inputTokens, long outputTokens) {
    public record Citation(UUID materialId, String fileName, int startOffset, int endOffset, String quote) { }
}
