package com.eecs3311.backend.resource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Stage 1 concrete study resource: important concepts and their source-backed details. */
public final class ConceptSet extends StudyResource {
    public record Concept(String name, String description, List<String> evidence) {
        public Concept {
            if (name == null || name.strip().length() < 2 || name.length() > 100 || normalizedName(name).isBlank()
                    || description == null || description.strip().length() < 10 || description.length() > 500
                    || evidence == null || evidence.isEmpty() || evidence.size() > 3
                    || evidence.stream().anyMatch(quote -> quote == null || quote.strip().length() < 8 || quote.length() > 300))
                throw new IllegalArgumentException("A concept requires a name, explanation, and source quotations.");
            evidence = List.copyOf(evidence);
        }
    }
    private final List<Concept> concepts;
    private final String model;
    private final int sourceCharacters, sections, modelCalls;
    private final long inputTokens, outputTokens;
    private final String sourceHash;

    public ConceptSet(UUID id, UUID courseId, UUID materialId, Instant createdAt, List<Concept> concepts,
                      String model, int sourceCharacters, int sections, int modelCalls, long inputTokens, long outputTokens, String sourceHash) {
        super(id, courseId, materialId, createdAt);
        if (concepts == null || concepts.isEmpty() || concepts.size() > 10
                || concepts.stream().map(concept -> normalizedName(concept.name())).distinct().count() != concepts.size())
            throw new IllegalArgumentException("A concept set requires 1 to 10 distinct concepts.");
        this.concepts = List.copyOf(concepts);
        this.model = model; this.sourceCharacters = sourceCharacters; this.sections = sections; this.modelCalls = modelCalls;
        this.inputTokens = inputTokens; this.outputTokens = outputTokens; this.sourceHash = sourceHash;
    }
    public static String normalizedName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    @Override public String view() { return concepts.stream().map(concept -> concept.name() + ": " + concept.description()).collect(java.util.stream.Collectors.joining("\n")); }
    public List<Concept> getConcepts() { return concepts; }
    public String getModel() { return model; }
    public int getSourceCharacters() { return sourceCharacters; }
    public int getSections() { return sections; }
    public int getModelCalls() { return modelCalls; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    @JsonIgnore public String getSourceHash() { return sourceHash; }
}
