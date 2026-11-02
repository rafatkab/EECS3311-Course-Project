package com.eecs3311.backend.course;

import com.eecs3311.backend.material.LearningMaterial;
import com.eecs3311.backend.ai.StudyAssistantFacade;
import com.eecs3311.backend.resource.Summary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable course owned by one student; dates are inclusive. */
public record Course(UUID courseId, UUID ownerId, String name, String description,
                     LocalDate startDate, LocalDate endDate, Instant createdAt) {
    /** Stage 1 F02: create a material belonging to this course. */
    public LearningMaterial addMaterial(String fileName, String fileType, long sizeBytes, String text) {
        return new LearningMaterial(UUID.randomUUID(), courseId, fileName, fileType, sizeBytes, Instant.now(), text);
    }

    /** Stage 1 F03: request a resource for a material associated with this course. */
    public Summary generateStudyResource(LearningMaterial material, StudyAssistantFacade assistant) {
        if (!courseId.equals(material.courseId())) throw new IllegalArgumentException("The material does not belong to this course.");
        return assistant.summarize(material);
    }

    public Course {
        if (courseId == null || ownerId == null || createdAt == null) {
            throw new IllegalArgumentException("A course requires an identity and owner.");
        }
        name = name == null ? "" : name.strip();
        description = description == null ? "" : description.strip();
        if (name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("Course name is required and must be 120 characters or fewer.");
        }
        if (description.isBlank() || description.length() > 2000) {
            throw new IllegalArgumentException("Description is required and must be 2000 characters or fewer.");
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Choose valid dates; end date must be on or after start date.");
        }
    }
}
