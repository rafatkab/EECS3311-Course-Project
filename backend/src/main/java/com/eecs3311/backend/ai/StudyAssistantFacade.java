package com.eecs3311.backend.ai;

import com.eecs3311.backend.material.LearningMaterial;
import com.eecs3311.backend.resource.StudyResourceFactory;
import com.eecs3311.backend.resource.Summary;
import org.springframework.stereotype.Service;

/** Stage 1 Facade: callers do not coordinate model calls, validation, or resource construction. */
@Service
public class StudyAssistantFacade {
    private final ContentAgent contentAgent;
    private final StudyResourceFactory resources;
    public StudyAssistantFacade(ContentAgent contentAgent, StudyResourceFactory resources) {
        this.contentAgent = contentAgent;
        this.resources = resources;
    }
    public Summary summarize(LearningMaterial material) {
        var draft = contentAgent.summarize(material);
        return (Summary) resources.createResource("Summary", material, draft);
    }
}
