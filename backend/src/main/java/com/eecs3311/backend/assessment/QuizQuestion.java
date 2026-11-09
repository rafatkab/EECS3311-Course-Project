package com.eecs3311.backend.assessment;

import java.util.*;

/** Answer keys stay on the backend until an attempt is finished. */
public record QuizQuestion(UUID questionId, QuizRequest.Type type, String question, List<String> choices,
        String expectedAnswer, List<Flashcard.Citation> citations, int points) {
    public QuizQuestion { choices = List.copyOf(choices); citations = List.copyOf(citations); }
    public record PublicQuestion(UUID questionId, QuizRequest.Type type, String question, List<String> choices, int points) { }
    public PublicQuestion publicView() { return new PublicQuestion(questionId, type, question, choices, points); }
}
