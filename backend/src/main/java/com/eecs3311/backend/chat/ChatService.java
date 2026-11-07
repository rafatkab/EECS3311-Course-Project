package com.eecs3311.backend.chat;

import com.eecs3311.backend.ai.AiFailure;
import com.eecs3311.backend.ai.StudyAssistantFacade;
import com.eecs3311.backend.course.CourseService;
import com.eecs3311.backend.material.LearningMaterial;
import com.eecs3311.backend.material.MaterialRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@Service
public class ChatService {
    private final CourseService courses;
    private final MaterialRepository materials;
    private final ChatRepository chats;
    private final StudyAssistantFacade assistant;
    private final ReentrantLock[] locks = IntStream.range(0, 64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    public ChatService(CourseService courses, MaterialRepository materials, ChatRepository chats, StudyAssistantFacade assistant) {
        this.courses = courses; this.materials = materials; this.chats = chats; this.assistant = assistant;
    }
    public ChatTurn askQuestion(String username, UUID courseId, String value) {
        var course = courses.getCourse(username, courseId);
        String question = value == null ? "" : value.strip();
        if (question.isEmpty() || question.length() > 1000) throw new AiFailure(400, "Enter a question between 1 and 1000 characters.");
        var lock = locks[Math.floorMod(courseId.hashCode(), locks.length)];
        if (!lock.tryLock()) throw new AiFailure(409, "A question is already being answered for this course. Try again shortly.");
        try {
            var metadata = materials.findByCourse(courseId);
            if (metadata.size() > 50) throw new AiFailure(413, "Course chat supports up to 50 learning materials per course.");
            var context = new ArrayList<LearningMaterial>();
            long total = 0;
            for (var entry : metadata) {
                var material = materials.findByIdAndCourse(entry.materialId(), courseId).orElseThrow(() -> new AiFailure(409, "Course materials changed. Try again."));
                total += material.getContent().length();
                if (total > 1_000_000) throw new AiFailure(413, "Course chat supports up to 1,000,000 extracted characters across its materials.");
                context.add(material);
            }
            var conversation = new CourseChat(course, chats.history(courseId, null));
            // No DB transaction/connection is held while the provider generates the answer.
            var answer = course.askQuestion(question, conversation, context, assistant);
            return chats.save(new ChatTurn(UUID.randomUUID(), 0, courseId, Instant.now(), question, answer.answer(), answer.answerable(),
                    answer.citations(), answer.model(), answer.modelCalls(), answer.inputTokens(), answer.outputTokens()));
        } finally { lock.unlock(); }
    }
    public List<ChatTurn> viewHistory(String username, UUID courseId, Long before) {
        var course = courses.getCourse(username, courseId);
        if (before != null && before < 1) throw new AiFailure(400, "The history cursor must be a positive sequence number.");
        return new CourseChat(course, chats.history(courseId, before)).viewHistory();
    }
}
