package com.eecs3311.backend.ai;
import com.eecs3311.backend.planning.StudyPlan;
import com.eecs3311.backend.planning.StudyScheduler;
import com.eecs3311.backend.planning.PlanRequest;
import com.eecs3311.backend.planning.Calendar;
import com.eecs3311.backend.planning.StudyProfile;

import com.eecs3311.backend.material.LearningMaterial;
import com.eecs3311.backend.chat.ChatTurn;
import com.eecs3311.backend.course.Course;
import java.util.List;
import com.eecs3311.backend.resource.ConceptSet;
import com.eecs3311.backend.resource.StudyResourceFactory;
import com.eecs3311.backend.resource.Summary;
import org.springframework.stereotype.Service;

/** Stage 1 Facade: callers do not coordinate model calls, validation, or resource construction. */
@Service
public class StudyAssistantFacade {
    public ContentAgent.AnswerDraft answerQuestion(Course course, String question,
            List<LearningMaterial> materials, List<ChatTurn> history) {
        return contentAgent.answerQuestion(course, question, materials, history);
    }
    private final ContentAgent contentAgent;
    private final StudyResourceFactory resources;
    private final PlanningAgent planningAgent;
    private final StudyScheduler scheduler;
    public StudyAssistantFacade(ContentAgent contentAgent, StudyResourceFactory resources, PlanningAgent planningAgent, StudyScheduler scheduler) {
        this.contentAgent = contentAgent;
        this.resources = resources;
        this.planningAgent = planningAgent; this.scheduler = scheduler;
    }
    public StudyPlan generateStudyPlan(Course course, StudyProfile profile,
            Calendar calendar, PlanRequest request,
            List<LearningMaterial> materials) {
        var events = calendar.planningEvents(profile, request);
        var recommendations = planningAgent.generateStudyPlan(course, profile, request, materials, events);
        var starts = scheduler.allocate(course.courseId(), profile, request, events, java.time.Instant.now());
        return (StudyPlan) resources.createResource("StudyPlan", course, recommendations, profile, request, starts);
    }
    public Summary summarize(LearningMaterial material) {
        var draft = contentAgent.summarize(material);
        return (Summary) resources.createResource("Summary", material, draft);
    }

    public ConceptSet extractConcepts(LearningMaterial material) {
        var draft = contentAgent.extractConcepts(material);
        return (ConceptSet) resources.createResource("ConceptSet", material, draft);
    }
}
