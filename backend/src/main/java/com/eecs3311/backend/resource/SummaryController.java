package com.eecs3311.backend.resource;

import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/materials/{materialId}/summary")
public class SummaryController {
    private final SummaryService service;
    public SummaryController(SummaryService service) { this.service = service; }

    @PostMapping
    public Summary summarize(Principal principal, @PathVariable UUID courseId, @PathVariable UUID materialId,
                             @RequestParam(defaultValue = "false") boolean regenerate) {
        return service.summarize(principal.getName(), courseId, materialId, regenerate);
    }

    @GetMapping
    public Summary viewSummary(Principal principal, @PathVariable UUID courseId, @PathVariable UUID materialId) {
        return service.getSummary(principal.getName(), courseId, materialId);
    }
}
