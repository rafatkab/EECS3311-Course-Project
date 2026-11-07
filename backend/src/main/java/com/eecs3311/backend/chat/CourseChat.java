package com.eecs3311.backend.chat;

import com.eecs3311.backend.ai.ContentAgent;
import com.eecs3311.backend.ai.StudyAssistantFacade;
import com.eecs3311.backend.course.Course;
import com.eecs3311.backend.material.LearningMaterial;
import java.util.List;

/** Stage 1 course conversation. Persistence is handled by ChatService/ChatRepository. */
public record CourseChat(Course course, List<ChatTurn> history) {
    public CourseChat { history = List.copyOf(history); }
    public List<ChatTurn> viewHistory() { return history; }
    public ContentAgent.AnswerDraft askQuestion(String question, List<LearningMaterial> materials, StudyAssistantFacade assistant) {
        return assistant.answerQuestion(course, question, materials, viewHistory());
    }
}
