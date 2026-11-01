package com.eecs3311.backend.material;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMaterialRepository implements MaterialRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<LearningMaterial> mapper = (row, index) -> new LearningMaterial(
            row.getObject("material_id", UUID.class), row.getObject("course_id", UUID.class),
            row.getString("file_name"), row.getString("file_type"), row.getLong("size_bytes"),
            row.getTimestamp("upload_date").toInstant(), row.getString("extracted_content"));

    public JdbcMaterialRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void save(LearningMaterial material, byte[] original) {
        jdbc.update("INSERT INTO learning_materials (material_id, course_id, file_name, file_type, size_bytes, upload_date, extracted_content, original_file) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                material.materialId(), material.courseId(), material.fileName(), material.fileType(),
                material.sizeBytes(), Timestamp.from(material.uploadDate()), material.getContent(), original);
    }

    @Override public List<LearningMaterial> findByCourse(UUID courseId) {
        return jdbc.query("SELECT material_id, course_id, file_name, file_type, size_bytes, upload_date, '' AS extracted_content FROM learning_materials WHERE course_id = ? ORDER BY upload_date DESC, material_id", mapper, courseId);
    }

    @Override public Optional<LearningMaterial> findByIdAndCourse(UUID id, UUID courseId) {
        return jdbc.query("SELECT * FROM learning_materials WHERE material_id = ? AND course_id = ?", mapper, id, courseId).stream().findFirst();
    }

    @Override public byte[] original(UUID id, UUID courseId) {
        return jdbc.queryForObject("SELECT original_file FROM learning_materials WHERE material_id = ? AND course_id = ?", byte[].class, id, courseId);
    }
}
