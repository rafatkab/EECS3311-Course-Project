package com.eecs3311.views;

import com.eecs3311.course.CourseApi;
import com.eecs3311.course.LearningMaterial;
import com.eecs3311.course.ConceptSet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Runs long model requests outside Vaadin's session lock, with visible progress. */
public class ConceptActions extends Div {
    private final CourseApi api;
    private final UUID courseId;
    private final LearningMaterial material;
    private final Executor executor;
    private final Consumer<String> showError;
    private final Button generate;
    private final Button view;
    private final Span progress = new Span();

    public ConceptActions(CourseApi api, UUID courseId, LearningMaterial material, Executor executor, Consumer<String> showError) {
        this.api = api; this.courseId = courseId; this.material = material; this.executor = executor; this.showError = showError;
        generate = new Button("Extract Concepts", event -> load(() -> api.extractConcepts(courseId, material.materialId(), false), "Extracting concepts…"));
        view = new Button("View saved concepts", event -> load(() -> api.viewConcepts(courseId, material.materialId()), "Loading concepts…"));
        progress.getElement().setAttribute("role", "status");
        addClassName("concept-actions");
        add(generate, view, progress);
    }

    private void load(Supplier<ConceptSet> request, String message) {
        UI ui = UI.getCurrent();
        generate.setEnabled(false);
        view.setEnabled(false);
        progress.setText(message);
        showError.accept("");
        try {
            CompletableFuture.supplyAsync(request, executor).whenComplete((concepts, failure) -> {
                if (!ui.isAttached()) return;
                ui.access(() -> {
                    if (!isAttached()) return;
                    generate.setEnabled(true);
                    view.setEnabled(true);
                    progress.setText("");
                    if (failure == null) showConceptSet(concepts);
                    else {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        showError.accept(cause instanceof CourseApi.ApiFailure ? cause.getMessage() : "Concept extraction failed. Please try again.");
                    }
                });
            });
        } catch (RuntimeException exception) {
            generate.setEnabled(true);
            view.setEnabled(true);
            progress.setText("");
            showError.accept("Concept requests are busy. Try again shortly.");
        }
    }

    private void showConceptSet(ConceptSet concepts) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Concepts — " + material.fileName());
        dialog.setWidth("850px");
        Div list = new Div();
        list.getElement().setAttribute("data-concepts-id", concepts.resourceId().toString());
        list.getElement().setAttribute("role", "list");
        concepts.concepts().forEach(concept -> {
            Div item = new Div(new H3(concept.name()), new Paragraph(concept.description()));
            item.addClassName("concept-item");
            item.getElement().setAttribute("role", "listitem");
            concept.evidence().forEach(quote -> {
                Div quotation = new Div();
                quotation.setText(quote);
                quotation.addClassName("summary-quote");
                item.add(quotation);
            });
            list.add(item);
        });
        Paragraph metadata = new Paragraph("AI-generated · " + concepts.model() + " · " + concepts.sections() + " source section(s)");
        dialog.add(metadata, new Paragraph("Important concepts, key details, and supporting source quotations."), list,
                new Paragraph("Check these concepts against the source; verified quotations do not guarantee every generated statement is correct."));
        Button regenerate = new Button("Regenerate concepts", event -> {
            dialog.close();
            load(() -> api.extractConcepts(courseId, material.materialId(), true), "Regenerating concepts…");
        });
        regenerate.getElement().setAttribute("title", "Requests a new concept set from your configured AI provider.");
        dialog.getFooter().add(regenerate, new Button("Close", event -> dialog.close()));
        dialog.open();
    }
}
