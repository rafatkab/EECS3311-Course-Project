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
public class JdbcConceptSetRepository implements ConceptSetRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcConceptSetRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override public Optional<ConceptSet> find(UUID courseId, UUID materialId) {
        return jdbc.query("SELECT * FROM concept_sets WHERE course_id = ? AND material_id = ?", (row, index) -> {
            try {
                List<ConceptSet.Concept> concepts = json.readValue(row.getString("concepts"), new TypeReference<>() { });
                return new ConceptSet(row.getObject("resource_id", UUID.class), courseId, materialId,
                        row.getTimestamp("created_at").toInstant(), concepts, row.getString("model"),
                        row.getInt("source_characters"), row.getInt("sections"), row.getInt("model_calls"),
                        row.getLong("input_tokens"), row.getLong("output_tokens"), row.getString("source_hash"));
            } catch (java.io.IOException exception) { throw new java.sql.SQLException("Cannot read stored concepts."); }
        }, courseId, materialId).stream().findFirst();
    }
    @Override public void save(ConceptSet concepts) {
        try {
            jdbc.update("MERGE INTO concept_sets (resource_id, course_id, material_id, created_at, concepts, model, source_characters, sections, model_calls, input_tokens, output_tokens, source_hash) KEY(material_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    concepts.getResourceId(), concepts.getCourseId(), concepts.getMaterialId(), Timestamp.from(concepts.getCreatedAt()),
                    json.writeValueAsString(concepts.getConcepts()), concepts.getModel(), concepts.getSourceCharacters(),
                    concepts.getSections(), concepts.getModelCalls(), concepts.getInputTokens(), concepts.getOutputTokens(), concepts.getSourceHash());
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot store extracted concepts."); }
    }
}
