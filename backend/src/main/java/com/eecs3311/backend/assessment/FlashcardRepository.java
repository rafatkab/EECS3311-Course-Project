package com.eecs3311.backend.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class FlashcardRepository {
    public record DeckSummary(UUID resourceId, long sequence, Instant createdAt, int cardLimit, int cardCount, String focus) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public FlashcardRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public void save(FlashcardDeck deck) {
        try {
            jdbc.update("INSERT INTO flashcard_decks (resource_id, course_id, created_at, card_limit, card_count, focus, deck_json) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    deck.getResourceId(), deck.getCourseId(), Timestamp.from(deck.getCreatedAt()), deck.getCardLimit(), deck.viewCards().size(), deck.getRequest().focus(), json.writeValueAsString(deck));
        } catch (java.io.IOException exception) { throw new IllegalStateException("Could not save flashcard deck.", exception); }
    }
    public Optional<FlashcardDeck> find(UUID course, UUID deck) {
        return jdbc.query("SELECT deck_json FROM flashcard_decks WHERE course_id = ? AND resource_id = ?", (row, index) -> {
            try { return json.readValue(row.getString(1), FlashcardDeck.class); }
            catch (java.io.IOException exception) { throw new java.sql.SQLException("Could not read saved flashcards.", exception); }
        }, course, deck).stream().findFirst();
    }
    public List<DeckSummary> list(UUID course, Long before) {
        return jdbc.query("SELECT resource_id, sequence, created_at, card_limit, card_count, focus FROM flashcard_decks WHERE course_id = ? AND sequence < ? ORDER BY sequence DESC LIMIT 25",
                (row, index) -> new DeckSummary(row.getObject(1, UUID.class), row.getLong(2), row.getTimestamp(3).toInstant(), row.getInt(4), row.getInt(5), row.getString(6)), course, before == null ? Long.MAX_VALUE : before);
    }
}
