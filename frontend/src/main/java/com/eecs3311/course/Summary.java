package com.eecs3311.course;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Summary(UUID resourceId, UUID courseId, UUID materialId, Instant createdAt,
                      String summaryText, List<String> evidence, String model, int sourceCharacters,
                      int sections, int modelCalls, long inputTokens, long outputTokens) { }
