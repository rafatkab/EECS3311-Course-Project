package com.eecs3311.course;

import java.time.Instant;
import java.util.*;

public final class FlashcardData {
    private FlashcardData() { }
    public record Request(int cardLimit, List<UUID> materialIds, String focus) { }
    public record Citation(UUID materialId, String fileName, String quote) { }
    public record Card(String question, String answer, List<Citation> citations) { }
    public record Deck(UUID resourceId, Instant createdAt, Request request, List<Card> cards, String model) { }
    public record Saved(UUID resourceId, long sequence, Instant createdAt, int cardLimit, int cardCount, String focus) { }
}
