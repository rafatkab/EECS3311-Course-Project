package com.eecs3311.views;

import com.eecs3311.course.CourseApi;
import com.eecs3311.course.LearningMaterial;
import com.eecs3311.course.Summary;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Runs long model requests outside Vaadin's session lock, with visible progress. */
public class SummaryActions extends Div {
    private final CourseApi api;
    private final UUID courseId;
    private final LearningMaterial material;
    private final Executor executor;
    private final Consumer<String> showError;
    private final Button generate;
    private final Button view;
    private final Span progress = new Span();

    public SummaryActions(CourseApi api, UUID courseId, LearningMaterial material, Executor executor, Consumer<String> showError) {
        this.api = api; this.courseId = courseId; this.material = material; this.executor = executor; this.showError = showError;
        generate = new Button("Summarize document", event -> load(() -> api.summarize(courseId, material.materialId(), false), "Generating summary…"));
        view = new Button("View saved summary", event -> load(() -> api.viewSummary(courseId, material.materialId()), "Loading summary…"));
        progress.getElement().setAttribute("role", "status");
        addClassName("summary-actions");
        add(generate, view, progress);
    }

    private void load(Supplier<Summary> request, String message) {
        UI ui = UI.getCurrent();
        generate.setEnabled(false);
        view.setEnabled(false);
        progress.setText(message);
        showError.accept("");
        try {
            CompletableFuture.supplyAsync(request, executor).whenComplete((summary, failure) -> {
                if (!ui.isAttached()) return;
                ui.access(() -> {
                    if (!isAttached()) return;
                    generate.setEnabled(true);
                    view.setEnabled(true);
                    progress.setText("");
                    if (failure == null) showSummary(summary);
                    else {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        showError.accept(cause instanceof CourseApi.ApiFailure ? cause.getMessage() : "Summary generation failed. Please try again.");
                    }
                });
            });
        } catch (RuntimeException exception) {
            generate.setEnabled(true);
            view.setEnabled(true);
            progress.setText("");
            showError.accept("Summary requests are busy. Try again shortly.");
        }
    }

    private void showSummary(Summary summary) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Summary — " + material.fileName());
        dialog.setWidth("850px");
        Pre text = new Pre(summary.summaryText());
        text.getElement().setAttribute("data-summary-id", summary.resourceId().toString());
        text.addClassName("material-text");
        Div quotations = new Div();
        summary.evidence().forEach(quote -> {
            Div item = new Div();
            item.setText(quote);
            item.addClassName("summary-quote");
            quotations.add(item);
        });
        Paragraph metadata = new Paragraph("AI-generated · " + summary.model() + " · " + summary.sections() + " source section(s)");
        dialog.add(metadata, text, new H3("Supporting source quotations"), quotations,
                new Paragraph("Check the source when using this summary; verified quotations do not guarantee every generated statement is correct."));
        Button regenerate = new Button("Regenerate summary", event -> {
            dialog.close();
            load(() -> api.summarize(courseId, material.materialId(), true), "Regenerating summary…");
        });
        regenerate.getElement().setAttribute("title", "Requests a new summary from your configured AI provider.");
        dialog.getFooter().add(regenerate, new Button("Close", event -> dialog.close()));
        dialog.open();
    }
}
