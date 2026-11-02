package com.eecs3311.backend.resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSummaryRepository implements SummaryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcSummaryRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public Optional<Summary> find(UUID courseId, UUID materialId) {
        return jdbc.query("SELECT * FROM summaries WHERE course_id = ? AND material_id = ?", (row, index) -> {
            try {
                List<String> evidence = json.readValue(row.getString("evidence"), new TypeReference<>() { });
                return new Summary(row.getObject("resource_id", UUID.class), courseId, materialId,
                        row.getTimestamp("created_at").toInstant(), row.getString("summary_text"), evidence,
                        row.getString("model"), row.getInt("source_characters"), row.getInt("sections"),
                        row.getInt("model_calls"), row.getLong("input_tokens"), row.getLong("output_tokens"), row.getString("source_hash"));
            } catch (java.io.IOException exception) { throw new java.sql.SQLException("Cannot read stored summary evidence."); }
        }, courseId, materialId).stream().findFirst();
    }

    @Override public void save(Summary summary) {
        try {
            jdbc.update("MERGE INTO summaries (resource_id, course_id, material_id, created_at, summary_text, evidence, model, source_characters, sections, model_calls, input_tokens, output_tokens, source_hash) KEY(material_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    summary.getResourceId(), summary.getCourseId(), summary.getMaterialId(), Timestamp.from(summary.getCreatedAt()),
                    summary.getSummaryText(), json.writeValueAsString(summary.getEvidence()), summary.getModel(),
                    summary.getSourceCharacters(), summary.getSections(), summary.getModelCalls(),
                    summary.getInputTokens(), summary.getOutputTokens(), summary.getSourceHash());
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot store summary evidence."); }
    }
}
