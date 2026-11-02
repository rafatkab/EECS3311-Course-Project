package com.eecs3311.backend.resource;

import java.util.Optional;
import java.util.UUID;

public interface SummaryRepository {
    Optional<Summary> find(UUID courseId, UUID materialId);
    void save(Summary summary);
}
