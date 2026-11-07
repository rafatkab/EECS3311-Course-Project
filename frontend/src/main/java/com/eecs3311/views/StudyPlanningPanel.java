package com.eecs3311.views;

import com.eecs3311.course.Course;
import com.eecs3311.course.CourseApi;
import com.eecs3311.course.PlanningData.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** F06 forms and saved schedule. Critical text layout does not depend on theme CSS. */
public class StudyPlanningPanel extends Div {
    private final CourseApi api;
    private final Course course;
    private final Executor executor;
    private final Div controls = new Div(), windows = new Div(), output = new Div(), events = new Div();
    private final List<WindowRow> rows = new ArrayList<>();
    private final IntegerField weekly = integer("Weekly study minutes", "planning-weekly", 180, 15, 10080);
    private final TextField zone = new TextField("Time zone");
    private final TextArea preferences = new TextArea("Study preferences"), habits = new TextArea("Study habits"), assessment = new TextArea("Past assessment results or weak areas (optional)");
    private final DatePicker start = new DatePicker("Plan start date"), end = new DatePicker("Plan end date"), eventDate = new DatePicker("Event date");
    private final IntegerField minutes = integer("Minutes per session", "planning-minutes", 45, 15, 180), count = integer("Number of sessions", "planning-count", 4, 1, 30);
    private final Select<String> eventType = new Select<>();
    private final TextField eventTitle = new TextField("Event title");
    private final TimePicker eventStart = new TimePicker("Event start"), eventEnd = new TimePicker("Event end");
    private final Paragraph error = new Paragraph();
    private final Span progress = new Span();
    private Profile savedProfile;
    private boolean initialized;
    private boolean loaded;
    public StudyPlanningPanel(CourseApi api, Course course, Executor executor) {
        this.api = api; this.course = course; this.executor = executor;
        setId("study-planning"); addClassName("study-planning");
        controls.setId("planning-controls");
        getStyle().set("min-width", "0").set("max-width", "100%");
        zone.setId("planning-zone"); zone.setValue("America/Toronto"); preferences.setId("planning-preferences"); habits.setId("planning-habits");
        for (var text : List.of(preferences, habits, assessment)) { text.setWidthFull(); text.setMaxLength(text == assessment ? 2000 : 1000); }
        start.setId("planning-start"); end.setId("planning-end"); eventDate.setId("planning-event-date");
        LocalDate first = course.startDate().isAfter(LocalDate.now()) ? course.startDate() : LocalDate.now();
        start.setValue(first); end.setValue(first.plusDays(13).isAfter(course.endDate()) ? course.endDate() : first.plusDays(13)); eventDate.setValue(first);
        minutes.setStep(15); weekly.setStep(15);
        addWindow(new Availability(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0)));
        addWindow(new Availability(DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0)));
        addWindow(new Availability(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(20, 0)));
        Button save = new Button("Save study profile", click -> saveProfile()); save.setId("planning-save-profile");
        Button generate = new Button("Generate study plan", click -> generate()); generate.setId("planning-generate");
        Button refresh = new Button("Refresh planning data", click -> run(() -> { }, "Loading planning data…", false, false));
        eventType.setLabel("Event type"); eventType.setItems("BUSY", "EXAM", "QUIZ"); eventType.setValue("BUSY"); eventType.setId("planning-event-type");
        eventTitle.setId("planning-event-title"); eventTitle.setMaxLength(120);
        eventStart.setId("planning-event-start"); eventEnd.setId("planning-event-end");
        eventStart.setStep(Duration.ofMinutes(15)); eventEnd.setStep(Duration.ofMinutes(15)); eventStart.setValue(LocalTime.of(18, 0)); eventEnd.setValue(LocalTime.of(19, 0));
        Div dates = new Div(start, end, minutes, count); dates.addClassName("planning-fields");
        Div eventFields = new Div(eventType, eventTitle, eventDate, eventStart, eventEnd); eventFields.addClassName("planning-fields");
        controls.add(new H3("Study profile — shared across your courses"), new Div(weekly, zone), preferences, habits, windows,
                new Button("Add availability window", click -> addWindow(new Availability(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)))), save,
                new H3("Busy times and important dates"), new Paragraph("Add a busy period, quiz, or exam in your saved profile's time zone. Other courses' events also block study time."),
                eventFields, new Button("Add calendar event", click -> addEvent()), events,
                new H3("Generate a personalized plan"), dates, assessment,
                new Paragraph("Sessions fit your weekly budget and availability, avoid calendar conflicts, and finish before the earliest quiz or exam in this date range. Changes affect your next generated plan."),
                generate, refresh, output);
        error.addClassName("error-message"); error.getElement().setAttribute("role", "alert"); progress.getElement().setAttribute("role", "status");
        add(progress, error, controls);
        addAttachListener(attached -> { if (!initialized) { initialized = true; run(() -> { }, "Loading planning data…", true, false); } });
    }
    private static IntegerField integer(String label, String id, int value, int min, int max) {
        IntegerField field = new IntegerField(label); field.setId(id); field.setMin(min); field.setMax(max); field.setValue(value); return field;
    }
    private class WindowRow extends Div {
        final Select<DayOfWeek> day = new Select<>(); final TimePicker from = new TimePicker("Available from"), to = new TimePicker("Available until");
        WindowRow(Availability value) {
            addClassName("availability-window"); day.setLabel("Day"); day.setItems(DayOfWeek.values()); day.setValue(value.day());
            from.setStep(Duration.ofMinutes(15)); to.setStep(Duration.ofMinutes(15)); from.setValue(value.start()); to.setValue(value.end());
            add(day, from, to, new Button("Remove window", click -> { rows.remove(this); windows.remove(this); }));
        }
        Availability value() { return new Availability(day.getValue(), from.getValue(), to.getValue()); }
    }
    private void addWindow(Availability value) {
        if (rows.size() >= 28) { error.setText("Use at most 28 availability windows."); return; }
        var row = new WindowRow(value); rows.add(row); windows.add(row);
    }
    private void saveProfile() {
        if (weekly.isEmpty() || preferences.isEmpty() || habits.isEmpty() || rows.isEmpty()
                || rows.stream().anyMatch(row -> row.day.isEmpty() || row.from.isEmpty() || row.to.isEmpty())) { error.setText("Complete your weekly minutes, preferences, habits, and availability windows."); return; }
        var value = new Profile(weekly.getValue(), preferences.getValue(), habits.getValue(), zone.getValue(), rows.stream().map(WindowRow::value).toList());
        run(() -> api.saveStudyProfile(value), "Saving study profile…", true, false);
    }
    private void generate() {
        if (start.isEmpty() || end.isEmpty() || minutes.isEmpty() || count.isEmpty()) { error.setText("Complete the plan dates, session duration, and session count."); return; }
        var request = new Request(start.getValue(), end.getValue(), minutes.getValue(), count.getValue(), assessment.getValue());
        run(() -> api.generateStudyPlan(course.courseId(), request), "Planning topics and scheduling sessions…", false, true);
    }
    private void addEvent() {
        if (savedProfile == null) { error.setText("Save your study profile before entering local event times."); return; }
        if (eventDate.isEmpty() || eventStart.isEmpty() || eventEnd.isEmpty() || eventTitle.isEmpty()) { error.setText("Complete the event title, date, and times."); return; }
        var timeZone = ZoneId.of(savedProfile.zoneId());
        var value = new EventInput(eventType.getValue(), eventTitle.getValue(), eventDate.getValue().atTime(eventStart.getValue()).atZone(timeZone).toInstant(), eventDate.getValue().atTime(eventEnd.getValue()).atZone(timeZone).toInstant());
        run(() -> api.addCalendarEvent(course.courseId(), value), "Saving calendar event…", false, false);
    }
    private void run(Runnable action, String status, boolean fillProfile, boolean showPlan) {
        UI ui = getUI().orElseThrow(); controls.getElement().setProperty("inert", true); progress.setText(status); error.setText("");
        try {
            CompletableFuture.supplyAsync(() -> { action.run(); return api.planningState(course.courseId()); }, executor).whenComplete((state, failure) -> {
                if (!ui.isAttached()) return;
                ui.access(() -> {
                    if (!isAttached()) return;
                    controls.getElement().setProperty("inert", false); progress.setText("");
                    if (failure == null) render(state, fillProfile, showPlan);
                    else { Throwable cause = failure.getCause() == null ? failure : failure.getCause(); error.setText(cause instanceof CourseApi.ApiFailure ? cause.getMessage() : "Study planning failed. Try again shortly."); }
                });
            });
        } catch (RuntimeException exception) { controls.getElement().setProperty("inert", false); progress.setText(""); error.setText("Requests are busy. Try again shortly."); }
    }
    private void render(State state, boolean fillProfile, boolean showPlan) {
        savedProfile = state.profile();
        if (!loaded && state.plan() != null) {
            var request = state.plan().request(); start.setValue(request.startDate()); end.setValue(request.endDate());
            minutes.setValue(request.sessionMinutes()); count.setValue(request.sessionCount()); assessment.setValue(request.assessmentNotes());
        }
        loaded = true;
        if (fillProfile && savedProfile != null) {
            weekly.setValue(savedProfile.availableStudyTime()); zone.setValue(savedProfile.zoneId()); preferences.setValue(savedProfile.studyPreferences()); habits.setValue(savedProfile.studyHabits());
            rows.clear(); windows.removeAll(); savedProfile.availability().forEach(this::addWindow);
        }
        events.removeAll();
        ZoneId timeZone = ZoneId.of(savedProfile == null ? "America/Toronto" : savedProfile.zoneId());
        var format = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm z");
        for (var event : state.events()) {
            Paragraph label = new Paragraph(event.type() + " · " + event.title() + " · " + event.startTime().atZone(timeZone).format(format) + " to " + event.endTime().atZone(timeZone).format(format));
            Div row = new Div(label); row.addClassName("planning-event");
            if (event.planId() == null && course.courseId().equals(event.courseId())) row.add(new Button("Remove event", click -> run(() -> api.removeCalendarEvent(course.courseId(), event.eventId()), "Removing event…", false, false)));
            events.add(row);
        }
        output.removeAll();
        if (state.plan() == null) { output.add(new Paragraph("No saved study plan yet.")); return; }
        var plan = state.plan(); var planZone = ZoneId.of(plan.profile().zoneId());
        Div body = new Div(); body.getElement().setAttribute("data-plan-id", plan.resourceId().toString());
        body.add(new Paragraph("Saved plan · " + plan.sessions().size() + " sessions · " + plan.model() + " · " + planZone));
        body.add(new Paragraph("Priority topics (1 is highest): " + plan.priorityTopics().stream()
                .map(topic -> topic.priority() + ": " + topic.topic()).collect(java.util.stream.Collectors.joining("; "))));
        for (var session : plan.sessions()) {
            Paragraph instructions = new Paragraph(session.instructions()); instructions.getStyle().set("white-space", "pre-wrap");
            Div row = new Div(new H3(session.startTime().atZone(planZone).format(format) + " — " + session.topic()),
                    new Paragraph(session.duration() + " minutes · " + session.activityType()), instructions);
            row.addClassName("study-session"); row.getStyle().set("overflow-wrap", "anywhere");
            for (var citation : session.citations()) { Paragraph quote = new Paragraph(citation.quote()); quote.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere"); row.add(new Details("Source: " + citation.fileName(), quote)); }
            body.add(row);
        }
        Details saved = new Details("Saved study plan", body); saved.setOpened(showPlan); output.add(saved);
    }
}
