package com.eecs3311.backend.assessment;

import java.util.List;
import java.util.UUID;

public record Flashcard(String question, String answer, List<Citation> citations) {
    public record Citation(UUID materialId, String fileName, String quote) { }
    public Flashcard {
        if (question == null || question.strip().length() < 5 || question.length() > 240 || answer == null || answer.isBlank() || answer.length() > 600
                || citations == null || citations.isEmpty() || citations.size() > 3)
            throw new IllegalArgumentException("A flashcard needs a question, answer, and source quotations.");
        question = question.strip(); answer = answer.strip(); citations = List.copyOf(citations);
    }
}
