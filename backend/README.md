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

## Document summaries (F03)

Configure local Ollama as described in the root README. The backend still starts
without an AI model, so F01/F02 remain available. Attempting generation without a
configured model returns 503 with setup guidance; saved summaries do not require it.

- `POST /api/courses/{id}/materials/{materialId}/summary`: generate or return a cached summary.
- The same POST with `?regenerate=true`: request a replacement from the model.
- `GET /api/courses/{id}/materials/{materialId}/summary`: read the saved result, or 404.

All endpoints enforce course ownership; writes also require the existing CSRF token.
Results contain text, exact supporting quotations, source IDs, model, creation time,
section/call counts, and provider-reported tokens (`-1` means not reported).

`Course.generateStudyResource()` delegates to `StudyAssistantFacade`, which uses
`ContentAgent`, the shared `AIEngine.getInstance()`, and `StudyResourceFactory` to
create a `Summary` extending `StudyResource`. The repository persists the latest
summary per material with a source hash. Failed generation never overwrites it.
The simple factory currently has only the Summary product; other resource types
are not implemented. Strategy and Observer remain future work.

The agent splits up to 32,000 characters into approximately 6000-character sections,
summarizes each, and synthesizes multiple sections. The model selects numbered
source excerpts; the backend resolves them to exact quotations. It validates JSON,
length and selected IDs, with one repair attempt per response. Documents below 40 readable
characters or above the input limit are rejected before any model request. Limits:
14 calls, 180 seconds per operation, two concurrent model calls; one generation per
material. Prompt text treats uploaded material as untrusted data. Citation checks
do not prove every summary statement correct; users must review the source.

Provider defaults: local `http://localhost:11434/v1`, no key, `max_tokens`. Settings
include `study.ai.model`, `base-url`, `api-key`, `token-limit-field`, `timeout-seconds`
(1–120; default 45), and `max-output-tokens` (128–4096; default 1500).
For a hosted OpenAI-compatible provider, use HTTPS and a backend-only API key;
OpenAI models use `max_completion_tokens`. No requests are redirected and no raw
provider diagnostic or key is returned in application errors. See the official
[Ollama compatibility](https://docs.ollama.com/api/openai-compatibility) and
[OpenAI API](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create) documentation.

The provider/model must support schema-constrained structured outputs. The request
requires the summary and a bounded list of source-excerpt IDs; the backend validates
them independently and resolves quotations from the uploaded text.

Errors: insufficient content 400, oversized input 413, concurrent generation 409,
rate/busy limits 429, invalid provider output/unavailable provider 502, configuration
or provider authentication 503, timeout 504. Network/provider failures are not retried
automatically; repeated invalid structured output fails without saving.
