package com.eecs3311.backend.assessment;

import java.util.*;

public record QuizRequest(int questionCount, Difficulty difficulty, List<Type> questionTypes, List<UUID> materialIds, String topics) {
    public enum Difficulty { EASY, MEDIUM, HARD }
    public enum Type { SHORT_ANSWER, MULTIPLE_CHOICE }
    public QuizRequest {
        if (questionCount < 1 || questionCount > 20) throw new IllegalArgumentException("Choose 1 to 20 quiz questions.");
        if (difficulty == null) throw new IllegalArgumentException("Choose a difficulty.");
        questionTypes = questionTypes == null || questionTypes.isEmpty() ? List.of(Type.SHORT_ANSWER) : List.copyOf(questionTypes);
        if (questionTypes.size() > 2 || new HashSet<>(questionTypes).size() != questionTypes.size()) throw new IllegalArgumentException("Choose different supported question types.");
        materialIds = materialIds == null ? List.of() : List.copyOf(materialIds);
        if (materialIds.size() > 50 || new HashSet<>(materialIds).size() != materialIds.size()) throw new IllegalArgumentException("Select up to 50 different course materials.");
        topics = topics == null ? "" : topics.strip();
        if (topics.length() > 500) throw new IllegalArgumentException("Topics allow up to 500 characters.");
    }
}
