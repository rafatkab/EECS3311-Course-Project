# Vaadin Flow frontend

Java 17, Vaadin 24.10.7 (the last free Vaadin 24 release), Spring Boot, and Maven. The UI is implemented in Java under
`src/main/java/com/eecs3311/views`.

From this directory, with JDK 17+ installed and `JAVA_HOME` set:

```powershell
.\mvnw.cmd spring-boot:run
```

Open http://localhost:8080. The first run downloads Maven, dependencies, and frontend tooling.
Start and configure the backend first (see `../backend/README.md`). Sign in with
the username/password configured on the backend. Click **Create course**, fill all
four fields, and submit. The saved course appears on the dashboard. **Refresh**
reloads saved courses. Reloading the browser requires signing in again; courses persist.
Set `BACKEND_URL` if the backend is not at `http://localhost:8081`.
On macOS/Linux, use `sh mvnw spring-boot:run`.

Build a production JAR:

```powershell
.\mvnw.cmd clean package -Pproduction
java -jar target/frontend-0.0.1-SNAPSHOT.jar
```

UI and interaction handlers: `src/main/java/com/eecs3311/views/MainView.java`.
REST calls: `src/main/java/com/eecs3311/course/CourseApi.java`.
Styles: `src/main/frontend/themes/study-planner/styles.css`.
Course material uploads and previews: `src/main/java/com/eecs3311/views/CourseMaterialsPanel.java`.
Open a course card, select **Upload learning materials**, then use **View text** on
saved files. PDF, DOCX, and UTF-8 TXT are supported, up to 10 MiB each and 10 selected
files. **Download original** saves the complete uploaded document with its original
filename and contents; **View text** continues to show its extracted text. GUI uploads are independent: valid files are retained if another file fails.
Clear the upload list before selecting another batch. The backend also validates all files.
Credentials are held only in the signed-in view's server memory, never localStorage
or committed frontend configuration. Sign out invalidates the Vaadin session.

## Document summaries

Configure local Ollama on the backend first. **Summarize document** generates a
summary (or reuses the saved result). **View saved summary** makes no model call.
The dialog shows the summary, its model, and supporting source quotations.
**Regenerate summary** requests a new result; failure leaves the previous one intact.

`views/SummaryActions.java` runs requests in a bounded background executor configured
by `config/UiTaskConfiguration.java`, so the UI can show progress without holding
its session lock during inference. Summaries display as escaped plain text.

## F04 concepts

Each uploaded material also offers **Extract Concepts** and **View saved concepts**.
`views/ConceptActions.java` displays a list of concept names, explanations, and
source quotations, with **Regenerate concepts** in the dialog. It uses the same
bounded background executor as summaries, displays progress/errors, and renders
model text as escaped text. `course/ConceptSet.java` carries the API response;
`CourseApi.extractConcepts/viewConcepts` call the backend's `/concepts` endpoints.
The backend uses your existing Ollama settings; no frontend key is needed.
## F05 course chat

`views/CourseChatPanel.java` adds the course question box, saved history, progress,
errors, and expandable source quotations. `CourseApi.askQuestion/chatHistory` use
the same authenticated backend API as the CLI. Requests run in the existing bounded
background executor. Failed questions stay in the input; complete answers are saved
by the backend. History loads in pages of 50 with an older-messages button. Model
text is rendered as escaped text, and source viewing uses the authenticated client.
Chat answers preserve line breaks, wrap long lines and URLs, and expand vertically. Critical wrapping and width limits are applied directly to the components so missing or stale custom theme rules cannot turn an answer into a line extending beyond the screen. The F05 browser check compares full saved answer text and the final sentence at desktop and phone widths, including with the custom text class removed.

## F06 study planning

Expand **Study plan and availability** in a course. Save the shared weekly profile,
add blocking events or quiz/exam dates, and choose plan dates/session count/duration.
**Generate study plan** requests a new plan; **Saved study plan** displays priorities,
local session times, instructions, and expandable source quotations. Regeneration
replaces this course's generated calendar entries. Profile or event changes affect
the next generation, and failed requests preserve entered data and the saved plan.

`views/StudyPlanningPanel.java` uses `course/PlanningData.java` records and `CourseApi`
planning methods. Calls use the existing background executor and show progress/errors;
form interaction is temporarily disabled during a request. Current form values are
preserved on failure. Dates display in the saved plan's time zone. Busy/quiz/exam
inputs use the saved profile's time zone. Other courses' stored events also block time.
The full F10 calendar interface remains pending.
## F07 flashcards

Expand **Flashcards** in a course. Set the maximum, optional material selection,
and optional topic focus. Click **Generate flashcards**; the progress and error text
show the request state. The maximum is not a promise to pad unsupported questions.
Click a question or **Show answer** to reveal an answer and expandable sources.
Previous/next navigation hides the next answer. Saved decks reopen without AI;
refresh and older-deck controls load history in pages of 25. Failures keep form
values and the visible deck. Material choices refresh after uploads.

`views/FlashcardPanel.java` uses `course/FlashcardData.java` and `CourseApi` flashcard
methods. Text is escaped and wraps directly in components. Calls run in the existing
bounded executor. Dedicated section management, scoring, and spaced repetition are
outside F07; a separately uploaded section can be selected as a material.
F07 answers must appear in a cited excerpt (case and repeated whitespace are ignored).
The AI writes the question and selects the answer; unsupported cards are skipped after repair; only validated cards are saved. Questions and citation relevance still need review.

F07 count fix: short batches no longer end generation. Each new batch receives unused
source answers; repeated questions/answers are removed. Generation continues toward
the maximum until answers are exhausted or two batches add no cards (40 calls /
180 seconds maximum). A smaller saved deck shows an explanation. Restart both apps
and generate a new deck to apply this behavior to existing courses.

## F08 quizzes

`views/QuizPanel.java` provides count, difficulty, topic, type, and material controls;
saved quizzes; and start/save/resume/finish actions. `course/QuizData.java` and
`CourseApi` carry typed public quiz/attempt data. Generation and reads use the existing
background executor with progress/errors; pending requests temporarily disable the
panel. Failed requests preserve current answers and form input. Unsaved changes must
be saved or discarded before switching quizzes or generating another one.

Saved quizzes and the latest 25 attempts are available without AI. Drafts hide keys;
finished attempts show source-backed expected answers and expandable quotations.
Question and answer text is escaped and wraps at phone widths. A version conflict
requires reopening the attempt before another save. F09 now supplies automatic scores and feedback. Material selection represents separately
uploaded sections; a dedicated section editor is not included.
## F09 results

The quiz finish action is now **Submit quiz for grading**. `QuizData.State.gradeResult`
carries totals, question marks/feedback, weak areas, and provenance. `QuizPanel`
displays them alongside the existing answer/source review, with wrapped escaped text.
Pending grading disables the panel; failure keeps answers and the draft version so
the student can save or retry. Results reopen without AI. Older finished attempts
without a result show **Grade saved attempt**, using `CourseApi.gradeQuizAttempt`.
AI-assisted feedback is identified; exact/choice marks are labeled Answer key.
Starting a new attempt is the way to retry after a successful submission.