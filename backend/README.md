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
The simple factory constructs Summary, ConceptSet, and StudyPlan; other resource types
are not implemented. F09 now implements Strategy; F06 implements Observer for study-plan calendar synchronization.

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

## F04 concept extraction

`POST /api/courses/{id}/materials/{materialId}/concepts` extracts or returns the
source-matching saved set; add `?regenerate=true` for a replacement. `GET` at the
same path reads saved concepts without a model call. Authentication, ownership,
CSRF, input/provider limits, and safe errors match F03.

`StudyAssistantFacade.extractConcepts` delegates section planning, extraction,
consolidation, evidence verification, and one bounded repair to `ContentAgent`.
`StudyResourceFactory` creates a `ConceptSet` extending `StudyResource` from typed
`ConceptDraft` data. Concepts contain `name`, `description`, and exact source
`evidence`; the set includes model/usage/source metadata. Normalized duplicate
names and repeated quotation IDs are merged. Names may paraphrase the source. Evidence options cover readable source sentences, including long-sentence tails; dense lists use contiguous grouped windows instead of sampling. Invalid IDs, malformed/empty output, and invalid field lengths are rejected. The single repair request includes the specific failure and a bounded previous response as untrusted user data. `ConceptSetService` and
`JdbcConceptSetRepository` persist the latest set in `concept_sets`, atomically
replacing only successful results. Existing databases gain this table on startup;
courses, materials, and summaries remain intact. No additional dependency or
provider configuration is required. Concurrency guards are per material and
resource type; all AI calls share AIEngine's two-call limit.
## F05 course chat

`POST /api/courses/{id}/chat` accepts `{"question":"..."}` with the usual authentication
and CSRF token. `GET` returns the latest 50 complete turns in chronological order;
`?before=SEQUENCE` returns older turns. All reads and writes check course ownership.

`Course.askQuestion` → `CourseChat.askQuestion` → `StudyAssistantFacade.answerQuestion`
→ `ContentAgent.answerQuestion` uses the existing singleton AIEngine. The agent uses
`CourseContextRetriever` to rank 700-character passages using weighted word matches and common plural variants
and recent question topics, and sends up to six matching passages. Related follow-ups can include bounded recent conversation; new topics omit earlier answers.
This implements lexical retrieval followed by generation, without external search,
embeddings, LangChain, or new dependencies. Limits: 1–1000 characters per question,
50 materials and 1,000,000 extracted characters per course, two provider calls
(including one output repair), each using the configured timeout. Other failures are
not retried automatically. All model calls share the existing concurrency limit.

`ChatService`, `ChatRepository`, and `JdbcChatRepository` store each completed exchange
as one row in `course_chat_turns`. Citations retain material ID, filename, original
text offsets, and exact source text. Failed requests leave history unchanged;
unsupported questions save a clear insufficient-context reply with no citations. Broad overview requests use passages spread across the course materials even without matching keywords. For a specific question, no matching passages means no model call: model is course-search and token/call counts are zero. Recent topics can help generic follow-ups but do not override a new unrelated topic.
The schema adds the table without deleting existing data. History is readable with
the model offline. The model can still make factual or citation-relevance errors.
ChatAnswerValidator rejects obvious unfinished introductions and empty list markers before saving an answer. The agent requests one complete replacement; repeated failure leaves saved history unchanged. Valid short answers are accepted. Run scripts/verify-f05-completeness.ps1 from the repository root to check the Stage 2 PDF question against real Ollama through the CLI and browser.

## F06 planning and UC-11 profile

- `GET /api/study-profile`: the authenticated student's profile, or JSON null.
- `PUT /api/study-profile`: save the fields shown in `documentation/samples/study-profile.json`.
- `GET /api/courses/{id}/planning`: profile, latest course plan, and the student's events
  from the previous seven days through the next 366 days. The plan retains its own profile snapshot.
- `POST /api/courses/{id}/study-plan`: generate a replacement using `startDate`, `endDate`,
  `sessionMinutes`, `sessionCount`, and optional `assessmentNotes`.
- `POST /api/courses/{id}/calendar-events`: add a manual BUSY/EXAM/QUIZ event with title,
  `startTime`, and `endTime` (UTC instants). Manual events last at most seven days.
- `DELETE /api/courses/{id}/calendar-events/{eventId}`: remove an owned manual event.
  Generated sessions are replaced through plan regeneration.
- `GET /api/courses/{id}/calendar-events?from=INSTANT&to=INSTANT`: list the student's
  events in a range of at most 366 days. Events from other owned courses block scheduling too.

All course endpoints check ownership; every write requires CSRF protection. Availability
is 1–28 non-overlapping day/start/end windows, plus a weekly minute budget that fits
within them. Preferences/habits are required (1000 characters each); time zones use
IANA IDs. Profile update methods return validated immutable values. Plan dates must
fit the course and be today or later in the profile time zone. Durations are 15–180
minutes in 15-minute increments, counts 1–30, periods at most 56 days, assessment
notes at most 2000 characters. Limits for materials match F05.

`Course.generateStudyResource` → `StudyAssistantFacade.generateStudyPlan` →
`PlanningAgent.generateStudyPlan` uses course excerpts, the profile, manually supplied
assessment notes, and upcoming dates. The agent validates structured recommendations
and exact source IDs, with one correction attempt. `StudyScheduler` deterministically
allocates future slots and checks real elapsed durations, weekly budgets, other-course
study sessions, busy events, and the earliest upcoming quiz/exam deadline.

`StudyResourceFactory` creates a course-wide `StudyPlan` with `StudySession` records.
`StudyPlan` implements `ScheduleSubject`; `Calendar` implements `ScheduleObserver`.
`PlanPersistence.save` saves the latest plan, attaches the calendar, and calls
`notifyObservers`; `Calendar.update` replaces its generated events. Both writes share
one short transaction, and a failure rolls back both. No DB transaction is held during
model generation. `PlanningLock` coordinates profile/event changes and generation for
the same owner, including plans from different courses; competing writes receive 409.

H2 adds `study_profiles`, `study_plans`, and `calendar_events` on startup without deleting
existing data. The full F10 calendar UI, Course/Quiz subjects, and automatic assessment
integration remain future work. This feature uses Facade, Singleton, Simple Factory,
and Observer; it adds no application dependencies or model settings.
## F07 flashcards

- `POST /api/courses/{id}/flashcards`: generate and save a new deck (201). JSON:
  `{"cardLimit":5,"materialIds":[],"focus":""}`. Empty selection means all course materials.
- `GET /api/courses/{id}/flashcards[?before=SEQUENCE]`: newest saved deck metadata,
  up to 25 per page. Use the last sequence for the next page.
- `GET /api/courses/{id}/flashcards/{deckId}`: full saved deck, without calling AI.

All routes check ownership; POST requires CSRF. Limits: 1–20 cards, 50 materials,
1,000,000 extracted characters, topic up to 200 characters. Selected IDs must belong
to the course. Retrieval supplies up to six 700-character excerpts. AssessmentAgent
makes batches of up to three cards, validates the structured output/source IDs,
repairs once per batch, and removes normalized duplicate questions. Short factual
answers are valid; fewer cards are accepted. Bound: 40 calls / 180 seconds. Empty or
invalid final output saves nothing. A per-course generation lock returns 409 for
competing requests. Provider failures keep existing decks.

`Course.generateStudyResource` calls `StudyAssistantFacade.generateFlashcards`, then
`AssessmentAgent.generateFlashcards` and singleton `AIEngine.generate`. The factory
creates `FlashcardDeck`; `viewCards` supplies its cards. H2 adds `flashcard_decks`
without deleting existing data. Each deck snapshots its request, cards, quotations,
model, and token/call counts. Stored quotes need human review for relevance/accuracy.
CLI adds `flashcards COURSE_ID REQUEST_JSON_FILE`, `decks COURSE_ID [BEFORE_SEQUENCE]`,
and `deck COURSE_ID DECK_ID`. No new dependencies or provider configuration.
F07 answers must appear in a cited excerpt (case and repeated whitespace are ignored).
The AI writes the question and selects the answer; unsupported cards are skipped after repair; only validated cards are saved. Questions and citation relevance still need review.

F06 topic-diversity update: the planner now checks separate source passages for up to
12 specific topics, instead of repeating a single short overview. Repeated topics
receive different practice/review tasks. Instructions must be complete sentences.
Discovery is bounded to six passages, two calls per passage, and 180 seconds total.
Restart the backend and generate a new plan to update an existing saved schedule.

F07 count fix: short batches no longer end generation. Each new batch receives unused
source answers; repeated questions/answers are removed. Generation continues toward
the maximum until answers are exhausted or two batches add no cards (40 calls /
180 seconds maximum). A smaller saved deck shows an explanation. Restart both apps
and generate a new deck to apply this behavior to existing courses.

F07 validation recovery: mixed batches retain only valid cards. Single-fact retries
skip repeatedly unusable facts. If later model output fails, already validated cards
can be saved as a smaller new deck with an explanation; old decks remain unchanged.
No new deck is saved when no cards pass validation. Provider/transport failures still
show an error rather than inventing cards.

## F08 quiz API

- POST `/api/courses/{id}/quizzes`: `questionCount` (1–20), `difficulty`
  (EASY/MEDIUM/HARD), optional `topics` (500 characters), `materialIds` (empty = all),
  and `questionTypes` (SHORT_ANSWER/MULTIPLE_CHOICE; empty = SHORT_ANSWER). Returns 201.
- GET `/api/courses/{id}/quizzes[?before=SEQUENCE]`: latest 25 quiz summaries.
- GET `.../quizzes/{quizId}`: public questions and choices, no answer key.
- POST `.../quizzes/{quizId}/attempts`: start a draft (201).
- GET `.../quizzes/{quizId}/attempts`: latest 25 saved attempts.
- GET `.../quizzes/{quizId}/attempts/{attemptId}`: saved state. Keys/quotes are included
  only for finished attempts.
- PUT `.../quizzes/{quizId}/attempts/{attemptId}`: save a full response map with its version.
- POST `.../quizzes/{quizId}/attempts/{attemptId}/submit`: finish using the same input.
  All questions require an answer; this saves an immutable attempt and permits review.
  F09 now grades submissions and returns scores, feedback, and weak areas.

Every route checks course ownership; writes also need CSRF. Unknown question IDs,
answers over 4000 characters, unlisted MCQ choices, stale versions, and updates to
finished attempts are rejected. Drafts may be incomplete. The single version-checked
SQL update prevents silent overwrites from concurrent windows. No answer keys are
sent by ordinary quiz/start/draft endpoints. H2 adds `quizzes` and `quiz_attempts`.

`Course.generateStudyResource` → `StudyAssistantFacade.generateQuiz` →
`AssessmentAgent.generateQuiz` → `QuizGenerator` → singleton `AIEngine.generate`.
The helper targets distinct source facts, asks for an exact missing phrase and optional three
distractors, validates output, and resolves answers/citations on the backend. The
factory creates Quiz; `startQuiz` provides its public view. Mixed types alternate in
request order. Exactly the requested count must pass before saving. No partial quiz
or fake questions are saved. Failed generation preserves earlier quizzes/attempts.

Limits: 50 materials, 1,000,000 extracted characters, six 700-character excerpts,
40 model calls / 180 seconds. Each fact gets one repair; unusable facts are skipped within the overall call/time budget. Difficulty/relevance/ambiguity remain model limitations. No
new dependency, provider configuration, grading Strategy, or Quiz calendar behavior
is added. CLI quiz input files are bounded to 512 KiB.
F08 uses source-based fill-in-the-blank questions for both supported types. The AI
selects the removed phrase and MCQ distractors; Java constructs the stem from the
original fact. Restoring the expected phrase must reproduce the source statement.
This avoids mismatched generated questions/keys. Difficulty guides which phrase
is removed; semantic difficulty and distractor ambiguity still need review.

## F09 grading

Submitting POST `.../quizzes/{quizId}/attempts/{attemptId}/submit` now validates and
grades all responses before committing. The response includes `gradeResult`: earned
points, maximum points, percentage, per-question marks/feedback/methods, weak areas,
model identity, and reported token/call counts. The client cannot supply keys or scores.
GET of a finished attempt returns its cached result. POST `.../attempts/{attemptId}/grade`
grades an older finished attempt once; repeated calls return the saved grade. Drafts
must be submitted first. All routes check ownership and POST requires CSRF.

`Quiz.submitQuiz` calls `QuizGradingService.gradeQuiz`. This prototype-scoped Strategy
context calls `setStrategy` per question. `DeterministicGradingStrategy.grade` compares
objective choices (including binary True/False choices when present); F08 currently
creates MCQs and short answers. `AIGradingStrategy.grade` handles short answers, using
an exact NFC/whitespace/case/punctuation-normalized match without AI, otherwise singleton `AIEngine.generate`.
The fixed server rubric gives 0/0.5/1 credit fractions multiplied by stored points.
Java computes weighted totals. Grading validates keys/point values/source criteria
before any model calls. Contexts and model-call budgets are isolated per submission.

The model returns a categorical verdict and reason code. Java assigns 0/0.5/1 credit
and builds feedback and review facts from the saved key and source statement.
Limits: one model repair per question and 40 calls / 180 seconds. AI failures never produce fallback
marks or partial grades. UI responses stay in the form, and saved drafts/versions
remain unchanged on failure. `QuizSubmissionPersistence` atomically finishes the
attempt and inserts `quiz_grades`; storage failures roll back both. Attempt locks and
version checks reject competing writes with 409. Completed answers/grades are immutable.

`GradeRepository.recentForCourse` supplies the latest graded attempt for each of up to
five recent quizzes to PlanningAgent. Correct retakes replace earlier weaknesses in
that planning snapshot. Saved plans change only on regeneration. AI feedback may be
wrong and needs source review. Usage in saved results covers successful operations;
-1 token counts mean unavailable. No new dependency or AI configuration is required.