package com.eecs3311.views;

import com.eecs3311.course.Course;
import com.eecs3311.course.CourseApi;
import com.eecs3311.course.LearningMaterial;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.server.streams.UploadEvent;
import com.vaadin.flow.server.streams.DownloadEvent;
import com.vaadin.flow.server.VaadinResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** The selected course's materials workspace; no fake AI actions are exposed. */
public class CourseMaterialsPanel extends VerticalLayout {
    private final CourseApi api;
    private final Course course;
    private final java.util.concurrent.Executor summaryExecutor;
    private final Div materials = new Div();
    private final Paragraph error = new Paragraph();
    private final Paragraph result = new Paragraph();
    private final Span count = new Span();

    public CourseMaterialsPanel(CourseApi api, Course course, java.util.concurrent.Executor summaryExecutor, Runnable back) {
        this.api = api;
        this.course = course;
        this.summaryExecutor = summaryExecutor;
        setWidth("90%");
        setPadding(false);
        addClassName("materials-workspace");
        Button backButton = new Button("Back to courses", event -> back.run());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Span label = new Span("COURSE WORKSPACE");
        label.addClassName("eyebrow");
        Paragraph dates = new Paragraph(course.startDate() + " to " + course.endDate());
        dates.addClassName("course-dates");
        error.addClassName("error-message");
        error.getElement().setAttribute("role", "alert");
        result.addClassName("upload-result");
        result.getElement().setAttribute("role", "status");
        UI ui = UI.getCurrent();
        UploadHandler handler = new UploadHandler() {
            @Override public void handleUploadRequest(UploadEvent event) {
                try (var input = event.getInputStream()) {
                    byte[] data = input.readNBytes(10 * 1024 * 1024 + 1);
                    api.uploadMaterial(course.courseId(), event.getFileName(), data);
                    event.getResponse().setStatus(200);
                    ui.access(() -> {
                        result.setText("Uploaded " + event.getFileName() + ".");
                        refreshMaterials();
                    });
                } catch (CourseApi.ApiFailure | IOException failure) {
                    event.getResponse().setStatus(400);
                    String message = failure instanceof CourseApi.ApiFailure ? failure.getMessage() : "Could not read the uploaded file.";
                    ui.access(() -> error.setText(event.getFileName() + ": " + message));
                }
            }
            @Override public void responseHandled(boolean success, VaadinResponse response) {
                // Preserve the explicit status above so rejected files remain failed
                // uploads without passing expected validation errors to Vaadin's error handler.
                if (!success) response.setStatus(400);
            }
            @Override public long getFileSizeMax() { return 10 * 1024 * 1024; }
            @Override public long getRequestSizeMax() { return 10 * 1024 * 1024 + 65536; }
            @Override public long getFileCountMax() { return 1; }
        };
        Upload upload = new Upload(handler);
        upload.setId("material-upload");
        upload.setAcceptedFileTypes(".pdf", ".docx", ".txt");
        upload.setMaxFileSize(10 * 1024 * 1024);
        upload.setMaxFiles(10);
        Button uploadButton = new Button("Upload learning materials");
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        upload.setUploadButton(uploadButton);
        upload.addFileRejectedListener(event -> error.setText(event.getErrorMessage() + " Use PDF, DOCX, or UTF-8 TXT, up to 10 MiB each."));
        upload.addFailedListener(event -> {
            if (error.getText().isBlank()) error.setText("Upload failed. Refresh materials before retrying.");
        });
        Button clear = new Button("Clear upload list", event -> {
            upload.clearFileList();
            error.setText("");
            result.setText("");
        });
        Div uploadPanel = new Div(new H2("Add learning materials"),
                new Paragraph("PDF, DOCX, or UTF-8 TXT. Up to 10 files at a time, 10 MiB each. Scans need OCR first."), upload, clear);
        uploadPanel.addClassName("upload-panel");
        Button refresh = new Button("Refresh materials", event -> refreshMaterials());
        HorizontalLayout toolbar = new HorizontalLayout(new H2("Learning materials"), count, refresh);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(count);
        toolbar.addClassName("course-toolbar");
        materials.addClassName("material-list");
        add(backButton, label, new H1(course.name()), new Paragraph(course.description()), dates,
                uploadPanel, error, result, toolbar, materials);
        refreshMaterials();
    }

    private void refreshMaterials() {
        try {
            var list = api.listMaterials(course.courseId());
            materials.removeAll();
            count.setText(list.size() + (list.size() == 1 ? " file" : " files"));
            if (list.isEmpty()) {
                Div empty = new Div(new H3("Your learning materials go here"),
                        new Paragraph("Upload lecture notes or readings to keep them with this course."));
                empty.addClassName("empty-state");
                materials.add(empty);
            }
            for (LearningMaterial material : list) {
                String date = DateTimeFormatter.ofPattern("MMM d, uuuu").withZone(ZoneId.of("America/Toronto")).format(material.uploadDate());
                Paragraph details = new Paragraph(String.format(java.util.Locale.ROOT, "%.1f KiB · Uploaded %s", material.sizeBytes() / 1024.0, date));
                Button view = new Button("View text", event -> viewText(material));
                Anchor download = new Anchor((DownloadEvent event) -> {
                    try {
                        byte[] bytes = api.downloadMaterial(course.courseId(), material.materialId());
                        event.setFileName(material.fileName());
                        event.setContentType("application/octet-stream");
                        event.setContentLength(bytes.length);
                        event.getResponse().setHeader("Cache-Control", "no-store");
                        try (var output = event.getOutputStream()) { output.write(bytes); }
                    } catch (CourseApi.ApiFailure failure) {
                        event.getResponse().setStatus(503);
                        event.getUI().access(() -> error.setText("Download failed: " + failure.getMessage()));
                    }
                }, "Download original");
                download.getElement().setAttribute("download", material.fileName());
                Div actions = new Div(view, download);
                actions.addClassName("material-actions");
                Div row = new Div(new H3(material.fileName()), details, actions,
                        new SummaryActions(api, course.courseId(), material, summaryExecutor, error::setText));
                row.addClassName("material-row");
                materials.add(row);
            }
        } catch (CourseApi.ApiFailure failure) {
            error.setText(failure.getMessage());
        }
    }

    private void viewText(LearningMaterial material) {
        try {
            String content = api.materialContent(course.courseId(), material.materialId());
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle(material.fileName());
            dialog.setWidth("800px");
            Pre text = new Pre(content);
            text.addClassName("material-text");
            dialog.add(text);
            dialog.getFooter().add(new Button("Close", event -> dialog.close()));
            dialog.open();
        } catch (CourseApi.ApiFailure failure) {
            error.setText(failure.getMessage());
        }
    }
}
