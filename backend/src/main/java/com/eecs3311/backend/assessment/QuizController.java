package com.eecs3311.backend.assessment;

import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/courses/{course}/quizzes")
public class QuizController {
    private final QuizService service;
    public QuizController(QuizService service) { this.service = service; }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Quiz.View generate(Principal user, @PathVariable UUID course, @RequestBody QuizRequest request) { return service.generate(user.getName(), course, request); }
    @GetMapping public List<QuizRepository.Saved> list(Principal user, @PathVariable UUID course, @RequestParam(required=false) Long before) { return service.list(user.getName(), course, before); }
    @GetMapping("/{quiz}") public Quiz.View get(Principal user, @PathVariable UUID course, @PathVariable UUID quiz) { return service.get(user.getName(), course, quiz); }
    @PostMapping("/{quiz}/attempts") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public QuizAttempt.View start(Principal user, @PathVariable UUID course, @PathVariable UUID quiz) { return service.start(user.getName(), course, quiz); }
    @GetMapping("/{quiz}/attempts") public List<QuizAttempt> attempts(Principal user, @PathVariable UUID course, @PathVariable UUID quiz) { return service.attempts(user.getName(), course, quiz); }
    @GetMapping("/{quiz}/attempts/{attempt}") public QuizAttempt.View attempt(Principal user, @PathVariable UUID course, @PathVariable UUID quiz, @PathVariable UUID attempt) { return service.attempt(user.getName(), course, quiz, attempt); }
    @PutMapping("/{quiz}/attempts/{attempt}") public QuizAttempt.View save(Principal user, @PathVariable UUID course, @PathVariable UUID quiz, @PathVariable UUID attempt, @RequestBody QuizAttempt.Input input) { return service.save(user.getName(), course, quiz, attempt, input, false); }
    @PostMapping("/{quiz}/attempts/{attempt}/submit") public QuizAttempt.View submit(Principal user, @PathVariable UUID course, @PathVariable UUID quiz, @PathVariable UUID attempt, @RequestBody QuizAttempt.Input input) { return service.save(user.getName(), course, quiz, attempt, input, true); }
}
