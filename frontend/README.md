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