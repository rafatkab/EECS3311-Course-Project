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

## Learning material API (F02)

- `POST /api/courses/{id}/materials`: multipart fields named `files`; 1–10 files;
  Basic authentication, session cookie and CSRF token required. Returns 201 metadata.
- `GET /api/courses/{id}/materials`: list material metadata for an owned course.
- `GET /api/courses/{id}/materials/{materialId}/content`: extracted UTF-8 text.
- `GET /api/courses/{id}/materials/{materialId}/download`: original bytes as an attachment.

Allowed types: PDF, DOCX, UTF-8 TXT. File limit: 10 MiB; extracted text limit:
1,000,000 characters; PDF limit: 500 pages; DOCX expansion limit: 20 MiB/1000 entries.
PDFBox and POI parse the actual documents rather than trusting browser MIME types.
Empty, unreadable, encrypted, binary-text and text-free documents return 400;
unsupported extensions return 415; oversized files return 413. A missing or unowned
course/material returns 404. No OCR or LLM calls are performed.

`material/` contains the parser, service, domain record, and repository. The backend
`Course.addMaterial()` creates the association; `LearningMaterial.getContent()`
provides extracted text for later features. Metadata, original BLOB, and extracted
CLOB are saved together in H2. Invalid multi-file API requests roll back the entire
request. The GUI sends each file separately, so successful files remain if another
fails. The CLI can submit several paths in one atomic batch.
