package com.eecs3311.backend.resource;

import java.time.Instant;
import java.util.UUID;

/** Common identity and source association for generated study resources. */
public abstract class StudyResource {
    private final UUID resourceId;
    private final UUID courseId;
    private final UUID materialId;
    private final Instant createdAt;

    protected StudyResource(UUID resourceId, UUID courseId, UUID materialId, Instant createdAt) {
        this.resourceId = resourceId;
        this.courseId = courseId;
        this.materialId = materialId;
        this.createdAt = createdAt;
    }
    public UUID getResourceId() { return resourceId; }
    public UUID getCourseId() { return courseId; }
    public UUID getMaterialId() { return materialId; }
    public Instant getCreatedAt() { return createdAt; }
    public abstract String view();
}
