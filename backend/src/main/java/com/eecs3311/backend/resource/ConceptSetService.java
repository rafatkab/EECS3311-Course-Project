package com.eecs3311.backend.resource;

import com.eecs3311.backend.ai.AiFailure;
import com.eecs3311.backend.ai.StudyAssistantFacade;
import com.eecs3311.backend.course.CourseService;
import com.eecs3311.backend.material.MaterialService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@Service
public class ConceptSetService {
    private final CourseService courses;
    private final MaterialService materials;
    private final StudyAssistantFacade assistant;
    private final ConceptSetRepository conceptSets;
    // Fixed stripes avoid accumulating one lock per uploaded document.
    private final ReentrantLock[] locks = IntStream.range(0, 64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    public ConceptSetService(CourseService courses, MaterialService materials, StudyAssistantFacade assistant, ConceptSetRepository conceptSets) {
        this.courses = courses; this.materials = materials; this.assistant = assistant; this.conceptSets = conceptSets;
    }

    public ConceptSet extractConcepts(String username, UUID courseId, UUID materialId, boolean regenerate) {
        var course = courses.getCourse(username, courseId);
        var material = materials.getMaterial(username, courseId, materialId);
        var lock = locks[Math.floorMod(materialId.hashCode(), locks.length)];
        if (!lock.tryLock()) throw new AiFailure(409, "A concept set is already being generated. Please try again shortly.");
        try {
            var saved = conceptSets.find(courseId, materialId);
            String hash = StudyResourceFactory.sourceHash(material.getContent());
            if (!regenerate && saved.isPresent() && saved.get().getSourceHash().equals(hash)) return saved.get();
            // No database transaction is kept open during remote model generation.
            ConceptSet concepts = (ConceptSet) course.generateStudyResource("ConceptSet", material, assistant);
            conceptSets.save(concepts);
            return concepts;
        } finally { lock.unlock(); }
    }

    public ConceptSet getConceptSet(String username, UUID courseId, UUID materialId) {
        var material = materials.getMaterial(username, courseId, materialId);
        return conceptSets.find(courseId, materialId)
                .filter(concepts -> concepts.getSourceHash().equals(StudyResourceFactory.sourceHash(material.getContent())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No saved concept set for this material. Generate one first."));
    }
}
