package com.eecs3311.views;

import com.eecs3311.course.*;
import com.eecs3311.course.FlashcardData.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** F07: generation controls, saved deck history, and accessible question/answer cards. */
public class FlashcardPanel extends Div {
    private final CourseApi api;
    private final UUID course;
    private final Executor executor;
    private final IntegerField limit = new IntegerField("Maximum cards");
    private final MultiSelectComboBox<LearningMaterial> materials = new MultiSelectComboBox<>("Materials / sections");
    private final TextField focus = new TextField("Topic focus (optional)");
    private final Select<Saved> saved = new Select<>();
    private final Div controls = new Div(), study = new Div(), answer = new Div();
    private final Paragraph error = new Paragraph();
    private final Span progress = new Span(), position = new Span(), provenance = new Span();
    private final NativeButton question = new NativeButton();
    private final Button flip = new Button("Show answer", e -> flip()), previous = new Button("Previous card", e -> move(-1)), next = new Button("Next card", e -> move(1));
    private final Button older = new Button("Load older decks", e -> loadHistory(true));
    private final List<Saved> history = new ArrayList<>();
    private Deck deck;
    private int index;
    private boolean initialized, loadingHistory, revealed;
    public FlashcardPanel(CourseApi api, UUID course, Executor executor) {
        this.api = api; this.course = course; this.executor = executor;
        setId("flashcards"); getStyle().set("min-width", "0").set("max-width", "100%");
        limit.setId("flashcard-limit"); limit.setMin(1); limit.setMax(20); limit.setValue(5);
        materials.setId("flashcard-materials"); materials.setItemLabelGenerator(LearningMaterial::fileName); materials.setWidthFull();
        materials.setHelperText("Leave empty to use all materials. Select files to limit the sections used.");
        focus.setId("flashcard-focus"); focus.setMaxLength(200); focus.setWidthFull();
        saved.setId("flashcard-saved"); saved.setLabel("Saved decks"); saved.setWidthFull();
        saved.setItemLabelGenerator(d -> d.createdAt().toString() + " · " + d.cardCount() + " cards" + (d.focus().isBlank() ? "" : " · " + d.focus()));
        saved.addValueChangeListener(e -> { if (!loadingHistory && e.getValue() != null) run(() -> api.flashcardDeck(course, e.getValue().resourceId()), this::show, "Opening deck…"); });
        var generate = new Button("Generate flashcards", e -> generate()); generate.setId("flashcard-generate");
        controls.add(limit, materials, focus, new Paragraph("The limit is a maximum. Short materials may produce fewer distinct cards."), generate,
                new Button("Refresh saved decks", e -> loadHistory(false)), saved, older);
        question.setId("flashcard-question"); question.addClickListener(e -> flip());
        question.getStyle().set("display", "block").set("width", "100%").set("text-align", "left").set("white-space", "pre-wrap")
                .set("overflow-wrap", "anywhere").set("padding", "1rem").set("font-size", "1.1rem");
        question.getElement().setAttribute("aria-controls", "flashcard-answer");
        answer.setId("flashcard-answer"); answer.getStyle().set("min-width", "0").set("max-width", "100%").set("overflow-wrap", "anywhere");
        flip.setId("flashcard-flip"); next.setId("flashcard-next"); previous.setId("flashcard-previous");
        study.add(new H3("Study cards"), position, question, flip, answer, new Div(previous, next), provenance); study.setVisible(false);
        error.getElement().setAttribute("role", "alert"); error.addClassName("error-message"); progress.getElement().setAttribute("role", "status");
        add(controls, progress, error, study);
        addAttachListener(e -> { if (!initialized) { initialized = true; loadHistory(false); } });
    }
    public void setMaterials(List<LearningMaterial> list) {
        var ids = materials.getSelectedItems().stream().map(LearningMaterial::materialId).collect(java.util.stream.Collectors.toSet());
        materials.setItems(list); materials.select(list.stream().filter(m -> ids.contains(m.materialId())).toList());
    }
    private void generate() {
        if (limit.isEmpty() || limit.getValue() < 1 || limit.getValue() > 20) { error.setText("Choose a maximum of 1 to 20 cards."); return; }
        var request = new Request(limit.getValue(), materials.getSelectedItems().stream().map(LearningMaterial::materialId).toList(), focus.getValue().strip());
        run(() -> api.generateFlashcards(course, request), result -> { show(result); loadHistory(false); }, "Generating and checking flashcards…");
    }
    private void loadHistory(boolean append) {
        Long before = append && !history.isEmpty() ? history.get(history.size() - 1).sequence() : null;
        run(() -> api.flashcardDecks(course, before), list -> {
            if (!append) history.clear();
            history.addAll(list); loadingHistory = true;
            saved.setItems(history);
            if (deck != null) history.stream().filter(s -> s.resourceId().equals(deck.resourceId())).findFirst().ifPresent(saved::setValue);
            loadingHistory = false; older.setVisible(list.size() == 25);
        }, "Loading saved decks…");
    }
    private void show(Deck result) { deck = result; index = 0; study.setVisible(true); render(); }
    private void move(int change) { index += change; render(); }
    private void render() {
        var card = deck.cards().get(index); question.setText(card.question());
        position.setText("Card " + (index + 1) + " of " + deck.cards().size());
        previous.setEnabled(index > 0); next.setEnabled(index + 1 < deck.cards().size());
        answer.removeAll(); var text = new Paragraph(card.answer()); text.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere"); answer.add(text);
        for (var citation : card.citations()) {
            var quote = new Paragraph(citation.quote()); quote.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
            answer.add(new Details("Source: " + citation.fileName(), quote));
        }
        provenance.setText((deck.cards().size() < deck.request().cardLimit()
                ? "Generated " + deck.cards().size() + " of up to " + deck.request().cardLimit() + " cards from the selected excerpts. More distinct source-backed cards could not be generated. " : "")
                + "AI-generated · " + deck.model() + " · Check the sources before relying on an answer.");
        revealed = true; flip();
    }
    private void flip() {
        revealed = !revealed; answer.setVisible(revealed); flip.setText(revealed ? "Hide answer" : "Show answer");
        question.getElement().setAttribute("aria-expanded", String.valueOf(revealed));
    }
    private <T> void run(Supplier<T> task, Consumer<T> success, String message) {
        UI ui = getUI().orElse(UI.getCurrent()); controls.getElement().setAttribute("inert", ""); progress.setText(message); error.setText("");
        try {
            CompletableFuture.supplyAsync(task, executor).whenComplete((result, failure) -> {
                if (!ui.isAttached()) return;
                ui.access(() -> {
                    controls.getElement().removeAttribute("inert"); progress.setText("");
                    if (failure == null) success.accept(result);
                    else { Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure; error.setText(cause instanceof CourseApi.ApiFailure ? cause.getMessage() : "Could not complete the flashcard request. Refresh saved decks before retrying."); }
                });
            });
        } catch (RejectedExecutionException e) { controls.getElement().removeAttribute("inert"); progress.setText(""); error.setText("The application is busy. Try again shortly."); }
    }
}
