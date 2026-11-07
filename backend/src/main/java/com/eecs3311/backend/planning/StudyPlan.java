package com.eecs3311.backend.planning;

import com.eecs3311.backend.resource.StudyResource;
import com.fasterxml.jackson.annotation.*;
import java.time.Instant;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class StudyPlan extends StudyResource implements ScheduleSubject {
    private final PlanRequest request;
    private final StudyProfile profile;
    private final List<StudyTopic> priorityTopics;
    private final List<StudySession> sessions;
    private final String model;
    private final int modelCalls;
    private final long inputTokens, outputTokens;
    private final Set<ScheduleObserver> observers = new LinkedHashSet<>();
    @JsonCreator public StudyPlan(@JsonProperty("resourceId") UUID id, @JsonProperty("courseId") UUID courseId,
            @JsonProperty("createdAt") Instant createdAt, @JsonProperty("request") PlanRequest request,
            @JsonProperty("profile") StudyProfile profile, @JsonProperty("priorityTopics") List<StudyTopic> topics,
            @JsonProperty("sessions") List<StudySession> sessions, @JsonProperty("model") String model,
            @JsonProperty("modelCalls") int modelCalls, @JsonProperty("inputTokens") long inputTokens, @JsonProperty("outputTokens") long outputTokens) {
        super(id, courseId, null, createdAt);
        if (topics == null || topics.isEmpty() || sessions == null || sessions.size() != request.sessionCount())
            throw new IllegalArgumentException("A plan needs topics and the requested number of sessions.");
        this.request = request; this.profile = profile; this.priorityTopics = List.copyOf(topics); this.sessions = List.copyOf(sessions);
        this.model = model; this.modelCalls = modelCalls; this.inputTokens = inputTokens; this.outputTokens = outputTokens;
    }
    public PlanRequest getRequest() { return request; }
    public StudyProfile getProfile() { return profile; }
    public List<StudyTopic> getPriorityTopics() { return priorityTopics; }
    public List<StudySession> getSessions() { return sessions; }
    public String getModel() { return model; }
    public int getModelCalls() { return modelCalls; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    @Override public String view() { return sessions.stream().map(session -> session.startTime() + " — " + session.topic()).collect(java.util.stream.Collectors.joining("\n")); }
    @Override public void attach(ScheduleObserver observer) { observers.add(Objects.requireNonNull(observer)); }
    @Override public void detach(ScheduleObserver observer) { observers.remove(observer); }
    @Override public void notifyObservers() { List.copyOf(observers).forEach(observer -> observer.update(this)); }
}
