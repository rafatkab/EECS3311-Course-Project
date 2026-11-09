package com.eecs3311.backend.assessment;

import com.eecs3311.backend.resource.StudyResource;
import com.fasterxml.jackson.annotation.*;
import java.time.Instant;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Quiz extends StudyResource {
    private final QuizRequest request;
    private final List<QuizQuestion> questions;
    private final String model;
    private final int modelCalls;
    private final long inputTokens, outputTokens;
    @JsonCreator public Quiz(@JsonProperty("resourceId") UUID id, @JsonProperty("courseId") UUID course,
            @JsonProperty("createdAt") Instant createdAt, @JsonProperty("request") QuizRequest request,
            @JsonProperty("questions") List<QuizQuestion> questions, @JsonProperty("model") String model,
            @JsonProperty("modelCalls") int calls, @JsonProperty("inputTokens") long inputTokens, @JsonProperty("outputTokens") long outputTokens) {
        super(id, course, null, createdAt);
        if (questions == null || questions.size() != request.questionCount()) throw new IllegalArgumentException("A quiz requires exactly the requested number of questions.");
        this.request = request; this.questions = List.copyOf(questions); this.model = model; this.modelCalls = calls;
        this.inputTokens = inputTokens; this.outputTokens = outputTokens;
    }
    public record View(UUID resourceId, Instant createdAt, QuizRequest request, List<QuizQuestion.PublicQuestion> questions, String model) { }
    public View startQuiz() { return new View(getResourceId(), getCreatedAt(), request, questions.stream().map(QuizQuestion::publicView).toList(), model); }
    public QuizRequest getRequest() { return request; }
    public List<QuizQuestion> getQuestions() { return questions; }
    public String getModel() { return model; }
    public int getModelCalls() { return modelCalls; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    @Override public String view() { return questions.stream().map(QuizQuestion::question).collect(java.util.stream.Collectors.joining("\n\n")); }
}
