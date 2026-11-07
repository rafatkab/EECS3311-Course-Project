package com.eecs3311.views;

import com.eecs3311.course.ChatTurn;
import com.eecs3311.course.CourseApi;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Course-scoped conversation; inference runs outside the Vaadin session lock. */
public class CourseChatPanel extends Div {
    private final CourseApi api;
    private final UUID courseId;
    private final Executor executor;
    private final List<ChatTurn> turns = new ArrayList<>();
    private final Div messages = new Div();
    private final TextArea question = new TextArea("Ask about this course");
    private final Button send = new Button("Send question", event -> ask());
    private final Button refresh = new Button("Refresh chat", event -> load(false));
    private final Button older = new Button("Load older messages", event -> load(true));
    private final Paragraph error = new Paragraph();
    private final Span progress = new Span();
    public CourseChatPanel(CourseApi api, UUID courseId, Executor executor) {
        this.api = api; this.courseId = courseId; this.executor = executor;
        addClassName("course-chat"); setId("course-chat");
        setWidthFull();
        getStyle().set("min-width", "0").set("max-width", "100%").set("box-sizing", "border-box");
        messages.getStyle().set("min-width", "0").set("max-width", "100%");
        question.setMaxLength(1000); question.setWidthFull(); question.setId("chat-question");
        question.setHelperText("Answers use this course's uploaded materials. Include the topic in follow-up questions.");
        send.setId("chat-send"); error.addClassName("error-message"); error.getElement().setAttribute("role", "alert");
        progress.getElement().setAttribute("role", "status");
        messages.getElement().setAttribute("aria-label", "Course chat messages");
        Div actions = new Div(send, refresh, older, progress); actions.addClassName("chat-actions");
        add(new H2("Course chat"), new Paragraph("Ask questions, review saved answers, and check their source quotations."),
                messages, question, actions, error);
        // Fetch after attachment so asynchronous completion has an attached UI.
        addAttachListener(event -> load(false));
    }
    private void ask() {
        String value = question.getValue().strip();
        if (value.isBlank()) { error.setText("Enter a question first."); return; }
        run(() -> api.askQuestion(courseId, value), "Finding sources and answering…", answer -> {
            turns.add(answer); question.clear(); render();
        });
    }
    private void load(boolean previous) {
        Long before = previous && !turns.isEmpty() ? turns.get(0).sequence() : null;
        run(() -> api.chatHistory(courseId, before), "Loading chat…", history -> {
            if (!previous) turns.clear();
            turns.addAll(0, history);
            render(); older.setEnabled(history.size() == 50);
        });
    }
    private <T> void run(Supplier<T> request, String status, java.util.function.Consumer<T> complete) {
        UI ui = getUI().orElseThrow();
        busy(true); progress.setText(status); error.setText("");
        try {
            CompletableFuture.supplyAsync(request, executor).whenComplete((result, failure) -> {
                if (!ui.isAttached()) return;
                ui.access(() -> {
                    if (!isAttached()) return;
                    busy(false); progress.setText("");
                    if (failure == null) complete.accept(result);
                    else {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        error.setText(cause instanceof CourseApi.ApiFailure ? cause.getMessage() : "Chat request failed. Try again shortly.");
                    }
                });
            });
        } catch (RuntimeException exception) { busy(false); progress.setText(""); error.setText("Requests are busy. Try again shortly."); }
    }
    private void busy(boolean value) { send.setEnabled(!value); refresh.setEnabled(!value); older.setEnabled(!value && !turns.isEmpty()); question.setEnabled(!value); }
    private void render() {
        messages.removeAll();
        if (turns.isEmpty()) messages.add(new Paragraph("No questions yet. Upload materials, then ask your first question."));
        for (var turn : turns) {
            Div item = new Div(new H3("You"), new Paragraph(turn.question()), new H3("Study assistant"));
            item.addClassName("chat-turn"); item.getElement().setAttribute("data-turn-id", turn.turnId().toString());
            item.getStyle().set("min-width", "0").set("max-width", "100%").set("overflow-wrap", "anywhere");
            Pre text = new Pre(turn.answer()); text.addClassNames("material-text", "chat-answer");
            // Pre defaults to an unwrapped line. Keep complete answers readable even without theme CSS.
            text.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere")
                    .set("min-width", "0").set("width", "100%").set("max-width", "100%")
                    .set("box-sizing", "border-box").set("max-height", "none").set("overflow", "visible");
            item.add(text);
            for (var citation : turn.citations()) {
                Div source = new Div();
                Paragraph quote = new Paragraph(citation.quote()); quote.addClassName("summary-quote");
                Button view = new Button("View source text", event -> {
                    try {
                        Dialog dialog = new Dialog(); dialog.setHeaderTitle(citation.fileName()); dialog.setWidth("800px");
                        Pre content = new Pre(api.materialContent(courseId, citation.materialId())); content.addClassName("material-text");
                        dialog.add(content); dialog.getFooter().add(new Button("Close", close -> dialog.close())); dialog.open();
                    } catch (CourseApi.ApiFailure failure) { error.setText(failure.getMessage()); }
                });
                source.add(quote, view); item.add(new Details("Source: " + citation.fileName(), source));
            }
            item.add(new Paragraph((turn.modelCalls() == 0 ? "Course search" : "AI-generated · " + turn.model()) + " · " + turn.createdAt().toString()
                    + ". Check the source before relying on an answer."));
            messages.add(item);
        }
    }
}
