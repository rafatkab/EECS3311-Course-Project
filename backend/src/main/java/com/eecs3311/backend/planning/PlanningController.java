package com.eecs3311.backend.planning;

import com.eecs3311.backend.course.User;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

@RestController
public class PlanningController {
    private final PlanningService service;
    public PlanningController(PlanningService service) { this.service = service; }
    public record ProfileInput(int availableStudyTime, String studyPreferences, String studyHabits, String zoneId, List<StudyProfile.Availability> availability) { }
    @GetMapping("/api/study-profile") public Object profile(Principal user) {
        var profile = service.profile(user.getName());
        return profile == null ? com.fasterxml.jackson.databind.node.NullNode.instance : profile;
    }
    @PutMapping("/api/study-profile") public StudyProfile saveProfile(Principal user, @RequestBody ProfileInput input) {
        return service.saveProfile(user.getName(), new StudyProfile(User.authenticated(user.getName()).userId(), input.availableStudyTime(), input.studyPreferences(), input.studyHabits(), input.zoneId(), input.availability()));
    }
    @GetMapping("/api/courses/{id}/planning") public PlanningService.State state(Principal user, @PathVariable UUID id) { return service.state(user.getName(), id); }
    @PostMapping("/api/courses/{id}/study-plan") public StudyPlan generate(Principal user, @PathVariable UUID id, @RequestBody PlanRequest request) { return service.generate(user.getName(), id, request); }
    @PostMapping("/api/courses/{id}/calendar-events") public CalendarEvent addEvent(Principal user, @PathVariable UUID id, @RequestBody PlanningService.EventInput input) { return service.addEvent(user.getName(), id, input); }
    @DeleteMapping("/api/courses/{id}/calendar-events/{event}") public void removeEvent(Principal user, @PathVariable UUID id, @PathVariable UUID event) { service.removeEvent(user.getName(), id, event); }
    @GetMapping("/api/courses/{id}/calendar-events") public List<CalendarEvent> events(Principal user, @PathVariable UUID id, @RequestParam Instant from, @RequestParam Instant to) { return service.events(user.getName(), id, from, to); }
}
