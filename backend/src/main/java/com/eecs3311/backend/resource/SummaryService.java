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
public class SummaryService {
    private final CourseService courses;
    private final MaterialService materials;
    private final StudyAssistantFacade assistant;
    private final SummaryRepository summaries;
    // Fixed stripes avoid accumulating one lock per uploaded document.
    private final ReentrantLock[] locks = IntStream.range(0, 64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    public SummaryService(CourseService courses, MaterialService materials, StudyAssistantFacade assistant, SummaryRepository summaries) {
        this.courses = courses; this.materials = materials; this.assistant = assistant; this.summaries = summaries;
    }

    public Summary summarize(String username, UUID courseId, UUID materialId, boolean regenerate) {
        var course = courses.getCourse(username, courseId);
        var material = materials.getMaterial(username, courseId, materialId);
        var lock = locks[Math.floorMod(materialId.hashCode(), locks.length)];
        if (!lock.tryLock()) throw new AiFailure(409, "A summary is already being generated. Please try again shortly.");
        try {
            var saved = summaries.find(courseId, materialId);
            String hash = StudyResourceFactory.sourceHash(material.getContent());
            if (!regenerate && saved.isPresent() && saved.get().getSourceHash().equals(hash)) return saved.get();
            // No database transaction is kept open during remote model generation.
            Summary summary = course.generateStudyResource(material, assistant);
            summaries.save(summary);
            return summary;
        } finally { lock.unlock(); }
    }

    public Summary getSummary(String username, UUID courseId, UUID materialId) {
        var material = materials.getMaterial(username, courseId, materialId);
        return summaries.find(courseId, materialId)
                .filter(summary -> summary.getSourceHash().equals(StudyResourceFactory.sourceHash(material.getContent())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No saved summary for this material. Generate one first."));
    }
}
