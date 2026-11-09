package com.eecs3311.backend.assessment;

import java.time.Instant;
import java.util.*;

public record QuizAttempt(UUID attemptId, UUID quizId, Instant createdAt, Instant submittedAt, int version, Map<UUID, String> responses) {
    public QuizAttempt { responses = Map.copyOf(responses); }
    public record Input(int version, Map<UUID, String> responses) { }
    public record View(Quiz.View quiz, QuizAttempt attempt, List<QuizQuestion> answerKey, GradeResult gradeResult) { }
}
