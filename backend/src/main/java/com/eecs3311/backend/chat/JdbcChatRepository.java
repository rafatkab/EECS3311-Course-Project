package com.eecs3311.backend.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class JdbcChatRepository implements ChatRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<ChatTurn> mapper;
    public JdbcChatRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
        mapper = (row, index) -> {
            try {
                return new ChatTurn(row.getObject("turn_id", UUID.class), row.getLong("sequence"), row.getObject("course_id", UUID.class),
                        row.getTimestamp("created_at").toInstant(), row.getString("question"), row.getString("answer"), row.getBoolean("answerable"),
                        json.readValue(row.getString("citations"), new TypeReference<List<ChatTurn.Citation>>() { }), row.getString("model"),
                        row.getInt("model_calls"), row.getLong("input_tokens"), row.getLong("output_tokens"));
            } catch (java.io.IOException exception) { throw new java.sql.SQLException("Cannot read saved chat sources."); }
        };
    }
    @Override public ChatTurn save(ChatTurn turn) {
        try {
            jdbc.update("INSERT INTO course_chat_turns (turn_id, course_id, created_at, question, answer, answerable, citations, model, model_calls, input_tokens, output_tokens) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    turn.turnId(), turn.courseId(), Timestamp.from(turn.createdAt()), turn.question(), turn.answer(), turn.answerable(),
                    json.writeValueAsString(turn.citations()), turn.model(), turn.modelCalls(), turn.inputTokens(), turn.outputTokens());
            return jdbc.queryForObject("SELECT * FROM course_chat_turns WHERE turn_id = ?", mapper, turn.turnId());
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot save chat sources."); }
    }
    @Override public List<ChatTurn> history(UUID courseId, Long before) {
        var turns = new ArrayList<>(jdbc.query("SELECT * FROM course_chat_turns WHERE course_id = ? AND sequence < ? ORDER BY sequence DESC LIMIT 50",
                mapper, courseId, before == null ? Long.MAX_VALUE : before));
        Collections.reverse(turns);
        return List.copyOf(turns);
    }
}
