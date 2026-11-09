package com.eecs3311.backend.assessment;

import java.util.*;

public record FlashcardRequest(int cardLimit, List<UUID> materialIds, String focus) {
    public FlashcardRequest {
        if (cardLimit < 1 || cardLimit > 20) throw new IllegalArgumentException("Choose a flashcard limit between 1 and 20.");
        materialIds = materialIds == null ? List.of() : List.copyOf(materialIds);
        if (materialIds.size() > 50 || new HashSet<>(materialIds).size() != materialIds.size())
            throw new IllegalArgumentException("Select up to 50 different course materials.");
        focus = focus == null ? "" : focus.strip();
        if (focus.length() > 200) throw new IllegalArgumentException("The topic focus allows up to 200 characters.");
    }
}
