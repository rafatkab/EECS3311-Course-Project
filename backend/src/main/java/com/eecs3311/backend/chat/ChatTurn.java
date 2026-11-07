package com.eecs3311.backend.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A complete saved exchange; failed requests never create partial turns. */
public record ChatTurn(UUID turnId, long sequence, UUID courseId, Instant createdAt, String question,
                       String answer, boolean answerable, List<Citation> citations, String model,
                       int modelCalls, long inputTokens, long outputTokens) {
    public ChatTurn { citations = List.copyOf(citations); }
    public record Citation(UUID materialId, String fileName, int startOffset, int endOffset, String quote) { }
}
