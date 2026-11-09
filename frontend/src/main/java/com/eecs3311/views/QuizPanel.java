package com.eecs3311.views;

import com.eecs3311.course.*;
import com.eecs3311.course.QuizData.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** F08: saved quizzes, resumable drafts, and post-attempt answer review; no automatic grading. */
public class QuizPanel extends Div {
    private final CourseApi api;
    private final UUID course;
    private final Executor executor;
    private final Div controls = new Div(), output = new Div();
    private final IntegerField count = new IntegerField("Number of questions");
    private final Select<String> difficulty = new Select<>();
    private final MultiSelectComboBox<String> types = new MultiSelectComboBox<>("Question types");
    private final MultiSelectComboBox<LearningMaterial> materials = new MultiSelectComboBox<>("Materials / sections");
    private final TextField topics = new TextField("Topics (optional)");
    private final Select<Saved> saved = new Select<>();
    private final Paragraph error = new Paragraph();
    private final Span progress = new Span();
    private final List<Saved> history = new ArrayList<>();
    private final Map<UUID,String> responses = new HashMap<>();
    private final Button older = new Button("Load older quizzes", e -> loadHistory(true));
    private Quiz quiz;
    private State state;
    private boolean initialized, refreshing, dirty;
    private int pending;
    public QuizPanel(CourseApi api, UUID course, Executor executor) {
        this.api = api; this.course = course; this.executor = executor; setId("quizzes");
        getStyle().set("min-width", "0").set("max-width", "100%").set("overflow-wrap", "anywhere");
        count.setId("quiz-count"); count.setMin(1); count.setMax(20); count.setValue(5);
        difficulty.setLabel("Difficulty"); difficulty.setItems("EASY", "MEDIUM", "HARD"); difficulty.setValue("MEDIUM"); difficulty.setId("quiz-difficulty");
        types.setItems("SHORT_ANSWER", "MULTIPLE_CHOICE"); types.select("SHORT_ANSWER"); types.setId("quiz-types"); types.setWidthFull();
        materials.setItemLabelGenerator(LearningMaterial::fileName); materials.setWidthFull(); materials.setId("quiz-materials");
        materials.setHelperText("Leave empty for all materials; select a separately uploaded section to limit the source.");
        topics.setMaxLength(500); topics.setWidthFull(); topics.setId("quiz-topics");
        var generate = new Button("Generate quiz", e -> generate()); generate.setId("quiz-generate");
        saved.setLabel("Saved quizzes"); saved.setWidthFull(); saved.setId("quiz-saved");
        saved.setItemLabelGenerator(q -> q.createdAt() + " · " + q.questionCount() + " questions · " + q.difficulty());
        saved.addValueChangeListener(e -> { if (!refreshing && e.getValue() != null) {
            if (dirty) { error.setText("Save your draft or discard changes before opening another quiz."); refreshing = true; saved.setValue(e.getOldValue()); refreshing = false; return; }
            run(() -> api.quiz(course, e.getValue().resourceId()), this::showQuiz, "Opening quiz…");
        } });
        controls.add(count, difficulty, types, materials, topics,
                new Paragraph("The requested count must be supported by the selected excerpts. Difficulty guides the AI; review generated questions before relying on them."),
                generate, new Button("Refresh saved quizzes", e -> loadHistory(false)), saved, older);
        error.addClassName("error-message"); error.getElement().setAttribute("role", "alert"); progress.getElement().setAttribute("role", "status");
        add(controls, progress, error, output);
        addAttachListener(e -> { if (!initialized) { initialized = true; loadHistory(false); } });
    }
    public void setMaterials(List<LearningMaterial> list) {
        var ids = materials.getSelectedItems().stream().map(LearningMaterial::materialId).collect(java.util.stream.Collectors.toSet());
        materials.setItems(list); materials.select(list.stream().filter(m -> ids.contains(m.materialId())).toList());
    }
    private void generate() {
        if (dirty) { error.setText("Save your draft or discard changes before generating another quiz."); return; }
        if (count.isEmpty() || count.getValue()<1 || count.getValue()>20 || difficulty.isEmpty()) { error.setText("Choose 1–20 questions and a difficulty."); return; }
        var selectedTypes = types.getSelectedItems().stream().sorted().toList();
        var request = new Request(count.getValue(), difficulty.getValue(), selectedTypes, materials.getSelectedItems().stream().map(LearningMaterial::materialId).toList(), topics.getValue());
        run(() -> api.generateQuiz(course, request), value -> { showQuiz(value); loadHistory(false); }, "Generating and validating quiz questions…");
    }
    private void loadHistory(boolean append) {
        Long before = append && !history.isEmpty() ? history.get(history.size()-1).sequence() : null;
        run(() -> api.quizzes(course, before), list -> {
            if (!append) history.clear(); history.addAll(list); refreshing = true; saved.setItems(history);
            if (quiz != null) history.stream().filter(q -> q.resourceId().equals(quiz.resourceId())).findFirst().ifPresent(saved::setValue);
            refreshing = false; older.setVisible(list.size()==25);
        }, "Loading saved quizzes…");
    }
    private void showQuiz(Quiz value) {
        quiz = value; state = null; dirty = false; responses.clear(); output.removeAll();
        output.add(new H3("Saved quiz"), new Paragraph(value.questions().size() + " questions · " + value.request().difficulty() + " · " + value.model()));
        var start = new Button("Start quiz", e -> run(() -> api.startQuiz(course, quiz.resourceId()), this::showAttempt, "Starting quiz…")); start.setId("quiz-start");
        output.add(start, new Paragraph("Submit your answers for a score, feedback, weak areas, and source-based answer review. Saved drafts remain editable."));
        run(() -> api.quizAttempts(course, quiz.resourceId()), attempts -> {
            if (attempts.isEmpty()) return;
            var select = new Select<Attempt>(); select.setLabel("Recent attempts (latest 25)"); select.setItems(attempts); select.setWidthFull(); select.setId("quiz-attempts");
            select.setItemLabelGenerator(a -> a.createdAt() + (a.submittedAt()==null ? " · Draft" : " · Finished"));
            select.addValueChangeListener(e -> { if (e.getValue()!=null) run(() -> api.quizAttempt(course, quiz.resourceId(), e.getValue().attemptId()), this::showAttempt, "Opening attempt…"); });
            output.add(select);
        }, "Loading attempts…");
    }
    private void showAttempt(State value) {
        state = value; quiz = value.quiz(); responses.clear(); responses.putAll(value.attempt().responses()); dirty = false;
        boolean finished = value.attempt().submittedAt()!=null;
        output.removeAll(); output.getElement().setAttribute("data-attempt-id", value.attempt().attemptId().toString());
        output.add(new H3(finished ? "Finished quiz — answer review" : "Quiz attempt"));
        if(value.gradeResult()!=null) {
            var grade=value.gradeResult();
            Div result=new Div(new H3("Quiz results"),new Paragraph(grade.feedback())); result.setId("quiz-grade");
            result.getElement().setAttribute("data-grade-id",grade.gradeId().toString());
            if(grade.weakAreas().isEmpty()) result.add(new Paragraph("No weak areas were identified in this attempt."));
            else {
                result.add(new H4("Areas to review"));
                for(String area:grade.weakAreas()) { var text=new Paragraph(area); text.getStyle().set("white-space","pre-wrap").set("overflow-wrap","anywhere"); result.add(text); }
            }
            result.add(new Paragraph(grade.modelCalls()>0?"Includes AI-assisted grading. Review the feedback against the sources. Saved weak areas will guide your next generated study plan.":"Graded from saved answer criteria without a model call. Saved weak areas will guide your next generated study plan."));
            output.add(result);
        }
        for (int i=0;i<quiz.questions().size();i++) {
            var question = quiz.questions().get(i); var prompt = new Paragraph((i+1) + ". " + question.question());
            prompt.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
            Div row = new Div(prompt); row.addClassName("quiz-question"); row.getElement().setAttribute("data-question-id", question.questionId().toString());
            if (question.type().equals("MULTIPLE_CHOICE")) {
                var choices = new RadioButtonGroup<String>(); choices.setItems(question.choices()); choices.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL); choices.setWidthFull();
                choices.setLabel("Choose one answer"); choices.setReadOnly(finished);
                if (!responses.getOrDefault(question.questionId(), "").isBlank()) choices.setValue(responses.get(question.questionId()));
                choices.addValueChangeListener(e -> { responses.put(question.questionId(), e.getValue()==null ? "" : e.getValue()); dirty = true; }); row.add(choices);
            } else {
                var text = new TextArea("Your answer"); text.setWidthFull(); text.setMaxLength(4000); text.setValue(responses.getOrDefault(question.questionId(), "")); text.setReadOnly(finished);
                text.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.EAGER);
                text.addValueChangeListener(e -> { responses.put(question.questionId(), e.getValue()); dirty = true; }); row.add(text);
            }
            if (finished) value.answerKey().stream().filter(key -> key.questionId().equals(question.questionId())).findFirst().ifPresent(key -> {
                var answer = new Paragraph("Expected answer: " + key.expectedAnswer()); answer.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere"); row.add(answer);
                for (var citation : key.citations()) { var quote = new Paragraph(citation.quote()); quote.getStyle().set("white-space", "pre-wrap"); row.add(new Details("Source: " + citation.fileName(), quote)); }
            });
            if(value.gradeResult()!=null) value.gradeResult().questions().stream().filter(g -> g.questionId().equals(question.questionId())).findFirst().ifPresent(g -> {
                var feedback=new Paragraph(g.earnedPoints()+" / "+g.maxPoints()+" points · "+(g.method().equals("AI")?"AI-assisted":"Answer key")+"\n"+g.feedback());
                feedback.addClassName("question-grade"); feedback.getStyle().set("white-space","pre-wrap").set("overflow-wrap","anywhere"); row.add(feedback);
            });
            output.add(row);
        }
        if (!finished) {
            var save = new Button("Save quiz draft", e -> save(false)); save.setId("quiz-save");
            var finish = new Button("Submit quiz for grading", e -> save(true)); finish.setId("quiz-finish");
            output.add(save, finish, new Button("Discard unsaved changes", e -> run(() -> api.quizAttempt(course, quiz.resourceId(), state.attempt().attemptId()), this::showAttempt, "Reopening saved answers…")));
        } else if(value.gradeResult()==null) {
            var grade=new Button("Grade saved attempt",e -> run(() -> api.gradeQuizAttempt(course,quiz.resourceId(),state.attempt().attemptId()),this::showAttempt,"Grading saved answers…"));
            grade.setId("quiz-grade-saved"); output.add(new Paragraph("This finished attempt has no saved grade yet."),grade);
        }
        output.add(new Button("Back to saved quiz", e -> { if (dirty) error.setText("Save or discard your unsaved answers first."); else showQuiz(quiz); }));
    }
    private void save(boolean finish) {
        var input = new QuizData.Input(state.attempt().version(), Map.copyOf(responses));
        run(() -> api.saveQuizAttempt(course, quiz.resourceId(), state.attempt().attemptId(), input, finish), this::showAttempt, finish ? "Grading your answers…" : "Saving quiz draft…");
    }
    private <T> void run(Supplier<T> task, Consumer<T> success, String message) {
        UI ui = getUI().orElse(UI.getCurrent()); pending++; getElement().setProperty("inert", true); progress.setText(message); error.setText("");
        try { CompletableFuture.supplyAsync(task, executor).whenComplete((result, failure) -> {
            if (!ui.isAttached()) return;
            ui.access(() -> { if (!isAttached()) return; pending--; getElement().setProperty("inert", pending>0); if (pending==0) progress.setText("");
                if (failure==null) success.accept(result);
                else { Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure; error.setText(cause instanceof CourseApi.ApiFailure ? cause.getMessage() : "Quiz request failed. Reopen saved data before retrying."); }
            });
        }); } catch (RejectedExecutionException e) { pending--; getElement().setProperty("inert", pending>0); if (pending==0) progress.setText(""); error.setText("The application is busy. Try again shortly."); }
    }
}
