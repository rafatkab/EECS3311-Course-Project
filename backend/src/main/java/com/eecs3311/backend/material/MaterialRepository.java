package com.eecs3311.backend.material;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository {
    void save(LearningMaterial material, byte[] original);
    List<LearningMaterial> findByCourse(UUID courseId);
    Optional<LearningMaterial> findByIdAndCourse(UUID id, UUID courseId);
    byte[] original(UUID id, UUID courseId);
}
