package com.eecs3311.course;

import java.time.Instant;
import java.util.UUID;

public record LearningMaterial(UUID materialId, UUID courseId, String fileName, String fileType,
                               long sizeBytes, Instant uploadDate) { }
