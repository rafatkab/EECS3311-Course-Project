package com.eecs3311.backend.resource;

import com.eecs3311.backend.ai.ContentAgent;
import com.eecs3311.backend.material.LearningMaterial;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Stage 1 simple Factory: clients request a resource without assembling its identity/provenance. */
@Component
public class StudyResourceFactory {
    public StudyResource createResource(String type, LearningMaterial material, ContentAgent.ResourceDraft data) {
        if ("ConceptSet".equals(type) && data instanceof ContentAgent.ConceptDraft draft) {
            return new ConceptSet(UUID.randomUUID(), material.courseId(), material.materialId(), Instant.now(),
                    draft.concepts(), draft.model(), draft.sourceCharacters(), draft.sections(), draft.modelCalls(),
                    draft.inputTokens(), draft.outputTokens(), sourceHash(material.getContent()));
        }
        if ("Summary".equals(type) && data instanceof ContentAgent.Draft draft) return new Summary(UUID.randomUUID(), material.courseId(), material.materialId(), Instant.now(),
                draft.summaryText(), draft.evidence(), draft.model(), draft.sourceCharacters(), draft.sections(),
                draft.modelCalls(), draft.inputTokens(), draft.outputTokens(), sourceHash(material.getContent()));
        throw new IllegalArgumentException("This resource type is not implemented or its draft data does not match.");
    }

    public static String sourceHash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable."); }
    }
}
