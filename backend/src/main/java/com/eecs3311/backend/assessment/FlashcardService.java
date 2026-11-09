package com.eecs3311.backend.assessment;

import com.eecs3311.backend.ai.*;
import com.eecs3311.backend.course.CourseService;
import com.eecs3311.backend.material.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@Service
public class FlashcardService {
    private final CourseService courses;
    private final MaterialRepository materials;
    private final StudyAssistantFacade assistant;
    private final FlashcardRepository decks;
    private final ReentrantLock[] locks = IntStream.range(0, 64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    public FlashcardService(CourseService courses, MaterialRepository materials, StudyAssistantFacade assistant, FlashcardRepository decks) {
        this.courses = courses; this.materials = materials; this.assistant = assistant; this.decks = decks;
    }
    public FlashcardDeck generate(String username, UUID courseId, FlashcardRequest request) {
        var course = courses.getCourse(username, courseId);
        var lock = locks[Math.floorMod(courseId.hashCode(), locks.length)];
        if (!lock.tryLock()) throw new AiFailure(409, "Flashcards are already being generated for this course. Try again shortly.");
        try {
            var ids = request.materialIds().isEmpty() ? materials.findByCourse(courseId).stream().map(LearningMaterial::materialId).toList() : request.materialIds();
            if (ids.isEmpty()) throw new AiFailure(400, "Upload readable course materials before generating flashcards.");
            if (ids.size() > 50) throw new AiFailure(413, "Select up to 50 materials for a flashcard deck.");
            var context = new ArrayList<LearningMaterial>(); long characters = 0;
            for (UUID id : ids) {
                var material = materials.findByIdAndCourse(id, courseId).orElseThrow(() -> new AiFailure(404, "A selected material was not found in this course."));
                characters += material.getContent().length();
                if (characters > 1_000_000) throw new AiFailure(413, "Selected flashcard materials exceed 1,000,000 extracted characters.");
                context.add(material);
            }
            var selected = new FlashcardRequest(request.cardLimit(), ids, request.focus());
            var deck = course.generateStudyResource(selected, context, assistant);
            decks.save(deck); return deck;
        } finally { lock.unlock(); }
    }
    public FlashcardDeck get(String username, UUID course, UUID deck) {
        courses.getCourse(username, course);
        return decks.find(course, deck).orElseThrow(() -> new AiFailure(404, "Flashcard deck not found in this course."));
    }
    public List<FlashcardRepository.DeckSummary> list(String username, UUID course, Long before) {
        courses.getCourse(username, course);
        if (before != null && before < 1) throw new AiFailure(400, "The deck history cursor must be a positive sequence number.");
        return decks.list(course, before);
    }
}
