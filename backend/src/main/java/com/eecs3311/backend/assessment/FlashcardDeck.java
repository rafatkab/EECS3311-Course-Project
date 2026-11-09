package com.eecs3311.backend.assessment;

import com.eecs3311.backend.resource.StudyResource;
import com.fasterxml.jackson.annotation.*;
import java.time.Instant;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class FlashcardDeck extends StudyResource {
    private final FlashcardRequest request;
    private final List<Flashcard> cards;
    private final String model;
    private final int modelCalls;
    private final long inputTokens, outputTokens;
    @JsonCreator public FlashcardDeck(@JsonProperty("resourceId") UUID id, @JsonProperty("courseId") UUID course,
            @JsonProperty("createdAt") Instant createdAt, @JsonProperty("request") FlashcardRequest request,
            @JsonProperty("cards") List<Flashcard> cards, @JsonProperty("model") String model,
            @JsonProperty("modelCalls") int modelCalls, @JsonProperty("inputTokens") long inputTokens, @JsonProperty("outputTokens") long outputTokens) {
        super(id, course, null, createdAt);
        if (cards == null || cards.isEmpty() || cards.size() > request.cardLimit()) throw new IllegalArgumentException("A deck needs 1 to the requested maximum number of cards.");
        this.request = request; this.cards = List.copyOf(cards); this.model = model; this.modelCalls = modelCalls; this.inputTokens = inputTokens; this.outputTokens = outputTokens;
    }
    public FlashcardRequest getRequest() { return request; }
    public int getCardLimit() { return request.cardLimit(); }
    public List<Flashcard> viewCards() { return cards; }
    public List<Flashcard> getCards() { return viewCards(); }
    public String getModel() { return model; }
    public int getModelCalls() { return modelCalls; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    @Override public String view() { return cards.stream().map(card -> card.question() + "\n" + card.answer()).collect(java.util.stream.Collectors.joining("\n\n")); }
}
