package com.eecs3311.backend.resource;

import java.util.Optional;
import java.util.UUID;

public interface ConceptSetRepository {
    Optional<ConceptSet> find(UUID courseId, UUID materialId);
    void save(ConceptSet concepts);
}
