package com.eecs3311.backend.material;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

/** Original metadata plus extracted text for later study-resource features. */
public record LearningMaterial(UUID materialId, UUID courseId, String fileName, String fileType,
                               long sizeBytes, Instant uploadDate, @JsonIgnore String content) {
    @JsonIgnore
    public String getContent() { return content; }
}
