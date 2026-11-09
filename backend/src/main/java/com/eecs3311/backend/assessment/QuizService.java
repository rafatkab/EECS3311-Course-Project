package com.eecs3311.backend.assessment;

import com.eecs3311.backend.ai.*;
import com.eecs3311.backend.course.CourseService;
import com.eecs3311.backend.material.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@Service
public class QuizService {
    private final CourseService courses;
    private final MaterialRepository materials;
    private final StudyAssistantFacade assistant;
    private final QuizRepository quizzes;
    private final GradeRepository grades;
    private final org.springframework.beans.factory.ObjectProvider<QuizGradingService> grading;
    private final QuizSubmissionPersistence submissions;
    private final ReentrantLock[] gradingLocks = IntStream.range(0,64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    private final ReentrantLock[] locks = IntStream.range(0,64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    public QuizService(CourseService courses, MaterialRepository materials, StudyAssistantFacade assistant, QuizRepository quizzes,
            GradeRepository grades,org.springframework.beans.factory.ObjectProvider<QuizGradingService> grading,QuizSubmissionPersistence submissions) {
        this.grades=grades; this.grading=grading; this.submissions=submissions;
        this.courses = courses; this.materials = materials; this.assistant = assistant; this.quizzes = quizzes;
    }
    public Quiz.View generate(String username, UUID courseId, QuizRequest request) {
        var course = courses.getCourse(username, courseId);
        var lock = locks[Math.floorMod(courseId.hashCode(), locks.length)];
        if (!lock.tryLock()) throw new AiFailure(409, "A quiz is already being generated for this course.");
        try {
            var ids = request.materialIds().isEmpty() ? materials.findByCourse(courseId).stream().map(LearningMaterial::materialId).toList() : request.materialIds();
            if (ids.isEmpty()) throw new AiFailure(400, "Upload course materials before generating a quiz.");
            if (ids.size() > 50) throw new AiFailure(413, "Select up to 50 materials.");
            var context = new ArrayList<LearningMaterial>(); long characters = 0;
            for (var id : ids) {
                var material = materials.findByIdAndCourse(id, courseId).orElseThrow(() -> new AiFailure(404, "Selected material not found in this course."));
                characters += material.getContent().length();
                if (characters > 1_000_000) throw new AiFailure(413, "Selected quiz materials exceed 1,000,000 extracted characters.");
                context.add(material);
            }
            var selected = new QuizRequest(request.questionCount(), request.difficulty(), request.questionTypes(), ids, request.topics());
            var quiz = course.generateStudyResource(selected, context, assistant);
            quizzes.save(quiz); return quiz.startQuiz();
        } finally { lock.unlock(); }
    }
    private Quiz owned(String username, UUID course, UUID quiz) {
        courses.getCourse(username, course);
        return quizzes.find(course, quiz).orElseThrow(() -> new AiFailure(404, "Quiz not found in this course."));
    }
    public Quiz.View get(String user, UUID course, UUID quiz) { return owned(user, course, quiz).startQuiz(); }
    public List<QuizRepository.Saved> list(String user, UUID course, Long before) {
        courses.getCourse(user, course);
        if (before != null && before < 1) throw new AiFailure(400, "Use a positive quiz history cursor.");
        return quizzes.list(course, before);
    }
    public QuizAttempt.View start(String user, UUID course, UUID quizId) {
        var quiz = owned(user, course, quizId);
        return view(quiz, quizzes.createAttempt(quizId));
    }
    public List<QuizAttempt> attempts(String user, UUID course, UUID quiz) { owned(user, course, quiz); return quizzes.attempts(quiz); }
    public QuizAttempt.View attempt(String user, UUID course, UUID quizId, UUID attempt) {
        var quiz = owned(user, course, quizId);
        return view(quiz, quizzes.attempt(quizId, attempt).orElseThrow(() -> new AiFailure(404, "Quiz attempt not found.")));
    }
    public QuizAttempt.View save(String user, UUID course, UUID quizId, UUID attemptId, QuizAttempt.Input input, boolean submit) {
        courses.getCourse(user,course);
        var lock=gradingLocks[Math.floorMod(attemptId.hashCode(),gradingLocks.length)];
        if(!lock.tryLock()) throw new AiFailure(409,"This attempt is being graded or saved. Try again shortly.");
        try { return saveLocked(user,course,quizId,attemptId,input,submit); } finally { lock.unlock(); }
    }
    private QuizAttempt.View saveLocked(String user, UUID course, UUID quizId, UUID attemptId, QuizAttempt.Input input, boolean submit) {
        var quiz = owned(user, course, quizId);
        var previous = quizzes.attempt(quizId, attemptId).orElseThrow(() -> new AiFailure(404, "Quiz attempt not found."));
        if (previous.submittedAt() != null) throw new AiFailure(409, "This attempt is finished. Start a new attempt to answer again.");
        if (input.version() != previous.version()) throw new AiFailure(409, "This attempt changed in another window. Reopen it before saving.");
        var responses = input.responses() == null ? Map.<UUID,String>of() : input.responses();
        if (responses.size() > quiz.getQuestions().size()) throw new AiFailure(400, "Responses include unknown quiz questions.");
        var questions = new HashMap<UUID,QuizQuestion>(); quiz.getQuestions().forEach(q -> questions.put(q.questionId(), q));
        var clean = new HashMap<UUID,String>();
        responses.forEach((id, text) -> {
            var question = questions.get(id);
            if (question == null || text == null || text.length() > 4000) throw new AiFailure(400, "Use known question IDs and answers of up to 4000 characters.");
            String value = text.strip();
            if (!value.isEmpty() && question.type() == QuizRequest.Type.MULTIPLE_CHOICE && !question.choices().contains(value)) throw new AiFailure(400, "Choose one of the question's listed answers.");
            clean.put(id, value);
        });
        if (submit && quiz.getQuestions().stream().anyMatch(q -> clean.getOrDefault(q.questionId(), "").isBlank())) throw new AiFailure(400, "Answer every question before finishing. You can save an incomplete draft.");
        var saved = new QuizAttempt(attemptId, quizId, previous.createdAt(), submit ? Instant.now() : null, previous.version()+1, clean);
        if(submit) {
            var result=quiz.submitQuiz(saved,grading.getObject());
            submissions.submit(saved,result);
        } else if (!quizzes.update(saved)) throw new AiFailure(409, "This attempt changed in another window. Reopen it before saving.");
        return view(quiz, saved);
    }
    public QuizAttempt.View gradeSaved(String user,UUID course,UUID quizId,UUID attemptId) {
        var quiz=owned(user,course,quizId);
        var lock=gradingLocks[Math.floorMod(attemptId.hashCode(),gradingLocks.length)];
        if(!lock.tryLock()) throw new AiFailure(409,"This attempt is already being graded. Try again shortly.");
        try {
            var attempt=quizzes.attempt(quizId,attemptId).orElseThrow(() -> new AiFailure(404,"Quiz attempt not found."));
            if(attempt.submittedAt()==null) throw new AiFailure(400,"Submit the completed attempt before requesting its grade.");
            if(grades.find(quizId,attemptId).isEmpty()) grades.save(quiz.submitQuiz(attempt,grading.getObject()));
            return view(quiz,attempt);
        } finally { lock.unlock(); }
    }
    private QuizAttempt.View view(Quiz quiz, QuizAttempt attempt) {
        return new QuizAttempt.View(quiz.startQuiz(), attempt, attempt.submittedAt() == null ? List.of() : quiz.getQuestions(),
                attempt.submittedAt()==null?null:grades.find(quiz.getResourceId(),attempt.attemptId()).orElse(null));
    }
}
