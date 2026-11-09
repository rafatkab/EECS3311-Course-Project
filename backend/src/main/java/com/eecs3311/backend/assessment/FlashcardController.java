package com.eecs3311.backend.assessment;

import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/courses/{course}/flashcards")
public class FlashcardController {
    private final FlashcardService service;
    public FlashcardController(FlashcardService service) { this.service = service; }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public FlashcardDeck generate(Principal user, @PathVariable UUID course, @RequestBody FlashcardRequest request) { return service.generate(user.getName(), course, request); }
    @GetMapping public List<FlashcardRepository.DeckSummary> list(Principal user, @PathVariable UUID course, @RequestParam(required = false) Long before) { return service.list(user.getName(), course, before); }
    @GetMapping("/{deck}") public FlashcardDeck get(Principal user, @PathVariable UUID course, @PathVariable UUID deck) { return service.get(user.getName(), course, deck); }
}
