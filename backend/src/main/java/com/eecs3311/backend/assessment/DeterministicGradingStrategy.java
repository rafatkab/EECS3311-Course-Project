package com.eecs3311.backend.assessment;

import org.springframework.stereotype.Component;
import java.util.List;

/** Objective choice grading, including binary True/False choices when present. */
@Component
public class DeterministicGradingStrategy implements GradingStrategy {
    @Override public GradeResult.QuestionGrade grade(QuizQuestion question,String response,GradingBudget budget) {
        boolean correct=question.expectedAnswer().equals(response.strip());
        return new GradeResult.QuestionGrade(question.questionId(),correct?question.points():0,question.points(),
                correct?"Correct. Your selected choice matches the saved answer key.":"Incorrect. The correct choice is: "+question.expectedAnswer(),
                correct?List.of():List.of(GradeResult.weakArea(question)),"DETERMINISTIC");
    }
}
