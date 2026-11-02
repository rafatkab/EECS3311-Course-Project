package com.eecs3311.backend.resource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Summary extends StudyResource {
    private final String summaryText;
    private final List<String> evidence;
    private final String model;
    private final int sourceCharacters;
    private final int sections;
    private final int modelCalls;
    private final long inputTokens;
    private final long outputTokens;
    private final String sourceHash;

    public Summary(UUID id, UUID courseId, UUID materialId, Instant createdAt, String text, List<String> evidence,
                   String model, int sourceCharacters, int sections, int modelCalls, long inputTokens, long outputTokens, String sourceHash) {
        super(id, courseId, materialId, createdAt);
        if (text == null || text.isBlank() || text.length() > 2000 || evidence == null || evidence.isEmpty()) {
            throw new IllegalArgumentException("A summary requires validated text and supporting quotations.");
        }
        this.summaryText = text;
        this.evidence = List.copyOf(evidence);
        this.model = model;
        this.sourceCharacters = sourceCharacters;
        this.sections = sections;
        this.modelCalls = modelCalls;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.sourceHash = sourceHash;
    }
    @Override public String view() { return summaryText; }
    public String getSummaryText() { return summaryText; }
    public List<String> getEvidence() { return evidence; }
    public String getModel() { return model; }
    public int getSourceCharacters() { return sourceCharacters; }
    public int getSections() { return sections; }
    public int getModelCalls() { return modelCalls; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    @JsonIgnore public String getSourceHash() { return sourceHash; }
}
