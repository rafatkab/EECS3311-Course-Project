package com.eecs3311.backend.assessment;

import com.eecs3311.backend.ai.AiFailure;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Scope;
import java.time.Instant;
import java.util.*;

/** GoF Strategy context. A fresh context is created for each submission, preventing strategy races. */
@Service
@Scope("prototype")
public class QuizGradingService {
    private final DeterministicGradingStrategy deterministic;
    private final AIGradingStrategy ai;
    private GradingStrategy strategy;
    public QuizGradingService(DeterministicGradingStrategy deterministic,AIGradingStrategy ai) { this.deterministic=deterministic; this.ai=ai; }
    public void setStrategy(GradingStrategy strategy) { this.strategy=Objects.requireNonNull(strategy); }
    public GradeResult gradeQuiz(Quiz quiz,QuizAttempt attempt) {
        validate(quiz,attempt);
        var budget=new GradingBudget(); var results=new ArrayList<GradeResult.QuestionGrade>(); var weak=new LinkedHashSet<String>();
        double earned=0; int total=0;
        for(var question:quiz.getQuestions()) {
            setStrategy(question.type()==QuizRequest.Type.MULTIPLE_CHOICE?deterministic:ai);
            var result=strategy.grade(question,attempt.responses().get(question.questionId()),budget);
            results.add(result); earned+=result.earnedPoints(); total+=result.maxPoints(); weak.addAll(result.weakAreas());
        }
        double percentage=Math.round(earned*10000.0/total)/100.0;
        return new GradeResult(UUID.randomUUID(),quiz.getResourceId(),attempt.attemptId(),Instant.now(),earned,total,percentage,
                String.format(java.util.Locale.ROOT,"You scored %.1f of %d points (%.2f%%).",earned,total,percentage),results,List.copyOf(weak),budget.calls()>0?ai.model():"answer-key",budget.calls(),budget.inputTokens(),budget.outputTokens());
    }
    private void validate(Quiz quiz,QuizAttempt attempt) {
        if(!quiz.getResourceId().equals(attempt.quizId()) || quiz.getQuestions().isEmpty() || quiz.getQuestions().size()!=quiz.getRequest().questionCount()) throw new AiFailure(400,"Quiz grading criteria are unavailable.");
        var ids=new HashSet<UUID>();
        for(var q:quiz.getQuestions()) {
            if(q.questionId()==null || !ids.add(q.questionId()) || q.type()==null || q.question()==null || q.question().isBlank() || q.points()<1 || q.points()>100 || q.expectedAnswer()==null || q.expectedAnswer().isBlank() || q.expectedAnswer().length()>4000) throw new AiFailure(400,"Quiz grading criteria are unavailable.");
            String response=attempt.responses().get(q.questionId());
            if(response==null || response.isBlank() || response.length()>4000) throw new AiFailure(400,"Answer every question before submitting for grading.");
            if(q.type()==QuizRequest.Type.MULTIPLE_CHOICE) {
                if(q.choices().size()<2 || new HashSet<>(q.choices()).size()!=q.choices().size() || !q.choices().contains(q.expectedAnswer())) throw new AiFailure(400,"Objective answer criteria are unavailable.");
                if(!q.choices().contains(response.strip())) throw new AiFailure(400,"Choose one of the listed answers.");
            } else if(q.citations().isEmpty() || q.citations().stream().noneMatch(c -> c.quote()!=null && c.quote().replaceAll("\\s+"," ").contains(q.expectedAnswer().replaceAll("\\s+"," ")))) throw new AiFailure(400,"Source-backed short-answer grading criteria are unavailable.");
        }
        if(attempt.responses().keySet().stream().anyMatch(id -> !ids.contains(id))) throw new AiFailure(400,"Responses include unknown questions.");
    }
}
