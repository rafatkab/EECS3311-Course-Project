# Spring Boot backend

Independent Java 17 / Spring Boot 3.5 application with Spring Web, Bean Validation,
and Actuator. It has its own Maven build and no dependency on the Vaadin frontend.

## Run

From `backend/`, with JDK 17+ installed and `JAVA_HOME` set:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend listens on http://localhost:8081; the frontend uses port 8080.
Check http://localhost:8081/actuator/health for `{"status":"UP"}`.
The root URL has no page or API mapping yet. Set `BACKEND_PORT` to override the port.

On macOS/Linux, use `sh mvnw` in place of `.\mvnw.cmd`. The first run requires
internet access to download Maven and dependencies.

## Verify and package

```powershell
.\mvnw.cmd clean verify
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Add REST controllers and application services under `com.eecs3311.backend`.
Frontend integration and database configuration can be added when the application
APIs and data model are defined.
