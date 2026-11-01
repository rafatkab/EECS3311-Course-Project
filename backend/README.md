# Spring Boot backend

Independent Java 17 / Spring Boot 3.5 application with Spring Web, Bean Validation,
and Actuator. It has its own Maven build and no dependency on the Vaadin frontend.

## Run

From `backend/`, with JDK 17+ installed and `JAVA_HOME` set:

```powershell
Copy-Item config.local.properties.example config.local.properties
```

Edit the ignored `config.local.properties` and replace the password placeholder.
Alternatively set `STUDY_USERNAME` and `STUDY_PASSWORD` in this terminal. The backend
refuses to start with a missing password or the example placeholder.

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
F01 is implemented by `course/` (domain, service, REST controller, JDBC repository).
The local H2 database is saved under ignored `data/` and survives restarts. Start
only one backend against a given database file. Tests use a separate in-memory DB.

## Course API

All `/api/**` endpoints require HTTP Basic authentication using your configured
student account. The owner is derived from the authenticated principal; clients
cannot choose another owner. Basic authentication is intended for local development;
use HTTPS before deploying beyond localhost. No browser CORS configuration is needed:
the Vaadin Java server calls this API.

- `GET /api/courses`: list the authenticated student's courses.
- `GET /api/courses/{uuid}`: retrieve an owned course (404 for another owner).
- `GET /api/csrf`: obtain the `X-CSRF-TOKEN` response header and session cookie.
- `POST /api/courses`: submit JSON with that header and cookie; returns 201 and Location.

```json
{"name":"EECS 3311","description":"Software design","startDate":"2026-09-01","endDate":"2026-12-20"}
```

All four fields are required. Name limit: 120 characters; description limit: 2000.
Dates use `YYYY-MM-DD`; end must be on or after start. Text is trimmed, blank text
is rejected, same-day courses and duplicate names are allowed. Invalid input returns
400 without saving. Invalid authentication returns 401; missing CSRF returns 403;
database failures return 503 with a safe message. Course editing/deletion and account
registration are not implemented in this F01 increment.
