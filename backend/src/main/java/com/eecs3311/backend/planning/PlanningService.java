package com.eecs3311.backend.planning;

import com.eecs3311.backend.ai.*;
import com.eecs3311.backend.course.*;
import com.eecs3311.backend.material.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class PlanningService {
    private final PlanningRepository repository;
    private final PlanningLock lock;
    private final CourseService courses;
    private final MaterialRepository materials;
    private final StudyAssistantFacade assistant;
    private final StudyScheduler scheduler;
    private final PlanPersistence persistence;
    public PlanningService(PlanningRepository repository, PlanningLock lock, CourseService courses, MaterialRepository materials,
            StudyAssistantFacade assistant, StudyScheduler scheduler, PlanPersistence persistence) {
        this.repository = repository; this.lock = lock; this.courses = courses; this.materials = materials; this.assistant = assistant; this.scheduler = scheduler; this.persistence = persistence;
    }
    public record State(StudyProfile profile, StudyPlan plan, List<CalendarEvent> events) { }
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public State state(String username, UUID courseId) {
        var course = courses.getCourse(username, courseId);
        Instant now = Instant.now();
        return new State(repository.profile(course.ownerId()).orElse(null), repository.plan(course.ownerId(), courseId).orElse(null),
                repository.events(course.ownerId(), now.minusSeconds(7 * 86400L), now.plusSeconds(366 * 86400L)));
    }
    public StudyProfile profile(String username) { return repository.profile(User.authenticated(username).userId()).orElse(null); }
    public StudyProfile saveProfile(String username, StudyProfile input) {
        UUID owner = User.authenticated(username).userId();
        return lock.run(owner, () -> {
            var current = repository.profile(owner).orElse(new StudyProfile(owner, input.availableStudyTime(), input.studyPreferences(), input.studyHabits(), input.zoneId(), input.availability()));
            // Update both dimensions together so valid reductions in availability are not rejected by old preferences.
            var updated = new StudyProfile(owner, input.availableStudyTime(), current.studyPreferences(), current.studyHabits(), input.zoneId(), input.availability())
                    .updateAvailability(input.availableStudyTime(), input.zoneId(), input.availability()).updatePreferences(input.studyPreferences(), input.studyHabits());
            repository.saveProfile(updated); return updated;
        });
    }
    public StudyPlan generate(String username, UUID courseId, PlanRequest request) {
        var course = courses.getCourse(username, courseId);
        return lock.run(course.ownerId(), () -> {
            var profile = repository.profile(course.ownerId()).orElseThrow(() -> new AiFailure(400, "Save your study profile and weekly availability before generating a plan."));
            LocalDate today = LocalDate.now(ZoneId.of(profile.zoneId()));
            if (request.startDate().isBefore(today) || request.startDate().isBefore(course.startDate()) || request.endDate().isAfter(course.endDate()))
                throw new AiFailure(400, "Plan dates must be today or later and within the course start/end dates.");
            var calendar = new Calendar(course.ownerId(), repository);
            scheduler.allocate(courseId, profile, request, calendar.planningEvents(profile, request), Instant.now());
            var list = materials.findByCourse(courseId);
            if (list.size() > 50) throw new AiFailure(413, "Planning supports up to 50 materials per course.");
            var context = new ArrayList<LearningMaterial>(); long count = 0;
            for (var entry : list) {
                var material = materials.findByIdAndCourse(entry.materialId(), courseId).orElseThrow(() -> new AiFailure(409, "Course materials changed. Try again."));
                count += material.getContent().length();
                if (count > 1_000_000) throw new AiFailure(413, "Planning supports up to 1,000,000 extracted course characters.");
                context.add(material);
            }
            var plan = course.generateStudyResource(profile, calendar, request, context, assistant);
            return persistence.save(plan, calendar);
        });
    }
    public record EventInput(String type, String title, Instant startTime, Instant endTime) { }
    public CalendarEvent addEvent(String username, UUID courseId, EventInput input) {
        var course = courses.getCourse(username, courseId);
        return lock.run(course.ownerId(), () -> {
            String title = input.title() == null ? "" : input.title().strip();
            if (!Set.of("BUSY", "EXAM", "QUIZ").contains(input.type() == null ? "" : input.type()) || title.isEmpty() || title.length() > 120
                    || input.startTime() == null || input.endTime() == null || !input.endTime().isAfter(input.startTime())
                    || Duration.between(input.startTime(), input.endTime()).compareTo(Duration.ofDays(7)) > 0)
                throw new AiFailure(400, "Choose BUSY, EXAM, or QUIZ, a title up to 120 characters, and an event lasting between 1 second and 7 days.");
            var event = new CalendarEvent(UUID.randomUUID(), course.ownerId(), courseId, null, input.type(), title, input.startTime(), input.endTime());
            new Calendar(course.ownerId(), repository).addEvent(event); return event;
        });
    }
    public void removeEvent(String username, UUID courseId, UUID eventId) {
        var course = courses.getCourse(username, courseId);
        lock.run(course.ownerId(), () -> {
            if (!new Calendar(course.ownerId(), repository).removeEvent(courseId, eventId)) throw new AiFailure(404, "No manual event found for this course. Generated sessions are replaced by regenerating the plan.");
            return null;
        });
    }
    public List<CalendarEvent> events(String username, UUID courseId, Instant from, Instant to) {
        var course = courses.getCourse(username, courseId);
        if (from == null || to == null || !to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0)
            throw new AiFailure(400, "Choose a calendar range of up to 366 days.");
        return new Calendar(course.ownerId(), repository).viewEvents(from, to);
    }
}
