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
Credentials are held only in the signed-in view's server memory, never localStorage
or committed frontend configuration. Sign out invalidates the Vaadin session.
