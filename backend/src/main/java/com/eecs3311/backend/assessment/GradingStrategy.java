package com.eecs3311.backend.assessment;

public interface GradingStrategy {
    GradeResult.QuestionGrade grade(QuizQuestion question, String response, GradingBudget budget);
}
