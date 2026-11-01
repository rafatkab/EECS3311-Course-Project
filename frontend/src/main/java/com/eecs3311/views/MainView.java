package com.eecs3311.views;

import com.eecs3311.course.Course;
import com.eecs3311.course.CourseApi;
import com.eecs3311.course.CourseApiFactory;
import com.eecs3311.course.CreateCourseRequest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route("")
@PageTitle("Courses | Study Planner")
public class MainView extends VerticalLayout {
    private final CourseApiFactory apiFactory;
    private CourseApi api;
    private final Div courseList = new Div();
    private final Paragraph status = new Paragraph();
    private final Span count = new Span();

    public MainView(CourseApiFactory apiFactory) {
        this.apiFactory = apiFactory;
        addClassName("app-shell");
        setPadding(false);
        showSignIn();
    }

    private void showSignIn() {
        removeAll();
        api = null;
        TextField username = new TextField("Username");
        username.setId("username");
        username.setRequiredIndicatorVisible(true);
        PasswordField password = new PasswordField("Password");
        password.setId("password");
        password.setRequiredIndicatorVisible(true);
        Paragraph error = new Paragraph();
        error.addClassName("error-message");
        error.getElement().setAttribute("role", "alert");
        Button signIn = new Button("Sign in", event -> {
            if (username.isEmpty() || password.isEmpty()) {
                error.setText("Enter your username and password.");
                return;
            }
            try {
                CourseApi connection = apiFactory.connect(username.getValue().strip(), password.getValue());
                List<Course> courses = connection.viewCourses();
                api = connection;
                password.clear();
                showDashboard(username.getValue().strip(), courses);
            } catch (CourseApi.ApiFailure failure) {
                error.setText(failure.getMessage());
            }
        });
        signIn.setId("sign-in");
        signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout panel = new VerticalLayout(new Span("STUDY PLANNER"),
                new H1("Make room for better studying."),
                new Paragraph("Your courses, materials, and study goals. One place to begin."),
                username, password, signIn, error);
        panel.addClassName("sign-in-panel");
        add(panel);
    }

    private void showDashboard(String username, List<Course> courses) {
        removeAll();
        Span brand = new Span("STUDY PLANNER");
        brand.addClassName("brand");
        Button signOut = new Button("Sign out", event -> {
            getUI().ifPresent(ui -> {
                ui.getSession().getSession().invalidate();
                ui.getPage().reload();
            });
        });
        HorizontalLayout navigation = new HorizontalLayout(brand, new Span(username), signOut);
        navigation.addClassName("navigation");
        navigation.setWidthFull();
        navigation.expand(brand);
        navigation.setAlignItems(Alignment.CENTER);
        Span eyebrow = new Span("YOUR WORKSPACE");
        eyebrow.addClassName("eyebrow");
        VerticalLayout hero = new VerticalLayout(eyebrow, new H1("A good plan starts with a course."),
                new Paragraph("Bring your courses together and take the semester one step at a time."));
        hero.addClassName("hero");
        Button create = new Button("+ Create course", event -> openCourseForm());
        create.setId("create-course");
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button refresh = new Button("Refresh", event -> refreshCourses());
        HorizontalLayout toolbar = new HorizontalLayout(new H2("My courses"), count, refresh, create);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(count);
        toolbar.addClassName("course-toolbar");
        courseList.addClassName("course-grid");
        status.addClassName("error-message");
        status.getElement().setAttribute("role", "alert");
        VerticalLayout workspace = new VerticalLayout(toolbar, status, courseList);
        workspace.addClassName("workspace");
        workspace.setWidth("90%");
        workspace.setPadding(false);
        add(navigation, hero, workspace);
        renderCourses(courses);
    }

    private void refreshCourses() {
        try {
            renderCourses(api.viewCourses());
            status.setText("");
        } catch (CourseApi.ApiFailure failure) {
            status.setText(failure.getMessage());
        }
    }

    private void renderCourses(List<Course> courses) {
        count.setText(courses.size() + (courses.size() == 1 ? " course" : " courses"));
        courseList.removeAll();
        if (courses.isEmpty()) {
            Div empty = new Div(new H3("Your semester starts here"),
                    new Paragraph("Create your first course to give your study plans a home."));
            empty.addClassName("empty-state");
            courseList.add(empty);
        }
        DateTimeFormatter date = DateTimeFormatter.ofPattern("MMM d, uuuu");
        for (Course course : courses) {
            Span label = new Span("COURSE");
            label.addClassName("eyebrow");
            Paragraph description = new Paragraph(course.description());
            description.addClassName("course-description");
            Paragraph dates = new Paragraph(date.format(course.startDate()) + " – " + date.format(course.endDate()));
            dates.addClassName("course-dates");
            Div card = new Div(label, new H3(course.name()), description, dates);
            card.addClassName("course-card");
            courseList.add(card);
        }
    }

    private void openCourseForm() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create a course");
        dialog.setWidth("580px");
        dialog.setCloseOnOutsideClick(false);
        TextField name = new TextField("Course name");
        name.setId("course-name");
        name.setPlaceholder("e.g. EECS 3311 — Software Design");
        name.setMaxLength(120);
        TextArea description = new TextArea("Description");
        description.setId("course-description");
        description.setPlaceholder("What will you be learning?");
        description.setMaxLength(2000);
        DatePicker start = new DatePicker("Start date");
        start.setId("course-start");
        DatePicker end = new DatePicker("End date");
        end.setId("course-end");
        start.setLocale(java.util.Locale.CANADA);
        end.setLocale(java.util.Locale.CANADA);
        start.addValueChangeListener(event -> end.setMin(event.getValue()));
        name.setRequiredIndicatorVisible(true);
        description.setRequiredIndicatorVisible(true);
        start.setRequiredIndicatorVisible(true);
        end.setRequiredIndicatorVisible(true);
        FormLayout form = new FormLayout(name, description, start, end);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("440px", 2));
        form.setColspan(name, 2);
        form.setColspan(description, 2);
        Paragraph error = new Paragraph();
        error.addClassName("error-message");
        error.getElement().setAttribute("role", "alert");
        Button save = new Button("Create course");
        save.setId("save-course");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> {
            name.setInvalid(name.getValue().isBlank());
            name.setErrorMessage("Enter a course name.");
            description.setInvalid(description.getValue().isBlank());
            description.setErrorMessage("Enter a description.");
            start.setInvalid(start.isEmpty() || start.isInvalid());
            start.setErrorMessage("Choose a valid start date.");
            end.setInvalid(end.isEmpty() || end.isInvalid() || (!start.isEmpty() && !end.isEmpty() && end.getValue().isBefore(start.getValue())));
            end.setErrorMessage("Choose an end date on or after the start date.");
            if (name.isInvalid() || description.isInvalid() || start.isInvalid() || end.isInvalid()) {
                error.setText("Check the highlighted fields. All four fields are required.");
                return;
            }
            save.setEnabled(false);
            try {
                api.createCourse(new CreateCourseRequest(name.getValue().strip(), description.getValue().strip(), start.getValue(), end.getValue()));
                dialog.close();
                Notification.show("Course created.");
                refreshCourses();
            } catch (CourseApi.ApiFailure failure) {
                error.setText(failure.getMessage());
            } finally {
                save.setEnabled(true);
            }
        });
        Button cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(new Paragraph("Give your course a name, a description, and a timeline."), form, error);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        name.focus();
    }
}
