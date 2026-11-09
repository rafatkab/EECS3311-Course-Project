package com.eecs3311.backend.assessment;

import java.time.Instant;
import java.util.*;

/** Immutable server-calculated totals and per-question feedback. */
public record GradeResult(UUID gradeId, UUID quizId, UUID attemptId, Instant gradedAt,
        double earnedPoints, int totalPoints, double percentage, String feedback,
        List<QuestionGrade> questions, List<String> weakAreas, String model, int modelCalls, long inputTokens, long outputTokens) {
    public record QuestionGrade(UUID questionId, double earnedPoints, int maxPoints, String feedback,
            List<String> weakAreas, String method) {
        public QuestionGrade {
            if (questionId == null || maxPoints < 1 || !Double.isFinite(earnedPoints) || earnedPoints < 0 || earnedPoints > maxPoints
                    || feedback == null || feedback.isBlank()) throw new IllegalArgumentException("Invalid question grade.");
            weakAreas = List.copyOf(weakAreas);
        }
    }
    public GradeResult { questions = List.copyOf(questions); weakAreas = List.copyOf(weakAreas); }
    static String weakArea(QuizQuestion question) {
        String text = question.question();
        if (text.contains("\n")) text = text.substring(text.indexOf('\n')+1);
        text = text.replace("____", question.expectedAnswer()).replaceAll("\\s+", " ").strip();
        return text.length()>200 ? text.substring(0,197)+"…" : text;
    }
}
