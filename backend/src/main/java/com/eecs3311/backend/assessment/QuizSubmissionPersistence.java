package com.eecs3311.backend.assessment;

import com.eecs3311.backend.ai.AiFailure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizSubmissionPersistence {
    private final QuizRepository quizzes;
    private final GradeRepository grades;
    public QuizSubmissionPersistence(QuizRepository quizzes,GradeRepository grades) { this.quizzes=quizzes; this.grades=grades; }
    @Transactional public void submit(QuizAttempt attempt,GradeResult result) {
        if(!quizzes.update(attempt)) throw new AiFailure(409,"This attempt changed during grading. Reopen it before submitting again.");
        grades.save(result);
    }
}
