package com.eecs3311.backend.resource;

import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/materials/{materialId}/concepts")
public class ConceptSetController {
    private final ConceptSetService service;
    public ConceptSetController(ConceptSetService service) { this.service = service; }

    @PostMapping
    public ConceptSet extractConcepts(Principal principal, @PathVariable UUID courseId, @PathVariable UUID materialId,
                             @RequestParam(defaultValue = "false") boolean regenerate) {
        return service.extractConcepts(principal.getName(), courseId, materialId, regenerate);
    }

    @GetMapping
    public ConceptSet viewConceptSet(Principal principal, @PathVariable UUID courseId, @PathVariable UUID materialId) {
        return service.getConceptSet(principal.getName(), courseId, materialId);
    }
}
