package com.eecs3311.backend.chat;

import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/chat")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service = service; }
    public record Question(String question) { }
    @PostMapping public ChatTurn askQuestion(Principal principal, @PathVariable UUID courseId, @RequestBody Question request) {
        return service.askQuestion(principal.getName(), courseId, request.question());
    }
    @GetMapping public List<ChatTurn> viewHistory(Principal principal, @PathVariable UUID courseId, @RequestParam(required = false) Long before) {
        return service.viewHistory(principal.getName(), courseId, before);
    }
}
