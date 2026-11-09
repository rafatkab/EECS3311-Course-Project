package com.eecs3311.course;

import java.time.Instant;
import java.util.*;

public final class QuizData {
    private QuizData() { }
    public record Request(int questionCount, String difficulty, List<String> questionTypes, List<UUID> materialIds, String topics) { }
    public record Question(UUID questionId, String type, String question, List<String> choices, int points) { }
    public record Quiz(UUID resourceId, Instant createdAt, Request request, List<Question> questions, String model) { }
    public record Saved(UUID resourceId, long sequence, Instant createdAt, int questionCount, String difficulty, String topics) { }
    public record Attempt(UUID attemptId, UUID quizId, Instant createdAt, Instant submittedAt, int version, Map<UUID,String> responses) { }
    public record Key(UUID questionId, String expectedAnswer, List<FlashcardData.Citation> citations) { }
    public record QuestionGrade(UUID questionId,double earnedPoints,int maxPoints,String feedback,List<String> weakAreas,String method) { }
    public record Grade(UUID gradeId,double earnedPoints,int totalPoints,double percentage,String feedback,List<QuestionGrade> questions,List<String> weakAreas,int modelCalls) { }
    public record State(Quiz quiz, Attempt attempt, List<Key> answerKey, Grade gradeResult) { }
    public record Input(int version, Map<UUID,String> responses) { }
}
