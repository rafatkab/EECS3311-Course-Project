# EECS3311 Course Project

- [Frontend](frontend/README.md): Vaadin Flow application using Java 17, Spring Boot, and Maven.
- [Backend](backend/README.md): independent Spring Boot REST backend on port 8081.
- `documentation/`: course project reports and diagrams.

Start the frontend:

```powershell
cd frontend
.\mvnw.cmd spring-boot:run
```

Requires JDK 17+ with `JAVA_HOME` set. Open <http://localhost:8080> after startup.

Start the backend in a separate terminal from the repository root:

```powershell
cd backend
Copy-Item config.local.properties.example config.local.properties
# Edit config.local.properties and replace the password placeholder before starting.
.\mvnw.cmd spring-boot:run
```

Verify it at <http://localhost:8081/actuator/health>.

F01 **Course Creation** is available in the frontend. Sign in with your locally
configured backend credentials, then create courses with a name, description, start
date, and end date. Courses are stored in the backend's local H2 database.

## Learning materials (F02)

Open a course by clicking its card (or focus it and press Enter). Select **Upload
learning materials** to choose one or more PDF, DOCX, or UTF-8 TXT files. Each file
must be at most 10 MiB. Saved materials appear in that course; **View text** displays
the extracted content; **Download original** saves the complete original file.
**Back to courses** returns to the dashboard.

Try `documentation/samples/f02-notes.txt`. Unsupported, empty, corrupt, encrypted,
or text-free files show an error. Scans require OCR outside the app. Upload and text
extraction are deterministic.

## Document summaries (F03, local Ollama)

Install [Ollama for Windows](https://docs.ollama.com/windows), then download the model:

```powershell
ollama pull qwen2.5:1.5b
```

Ollama must be running at `http://localhost:11434`. If it is not already running,
start it with `ollama serve`. Add these settings to the ignored
`backend/config.local.properties`, keeping your existing authentication settings:

```properties
study.ai.base-url=http://localhost:11434/v1
study.ai.model=qwen2.5:1.5b
study.ai.api-key=
study.ai.token-limit-field=max_tokens
```

Restart both apps. Open a course and click **Summarize document** beside a material.
The UI shows progress, then the summary and supporting source quotations. **View
saved summary** reads the stored result without a model call. **Regenerate summary**
requests a replacement; failures preserve the previous summary. Navigation does
not cancel an in-progress backend request; its result can be viewed later.

The agent summarizes sections, synthesizes longer documents, checks exact source
quotations, and retries malformed output once. Input must contain 40–32,000
characters. Quotes are checked, but the summary's factual accuracy still needs
human review. F04–F10 are not implemented. No hosted API key is needed for local Ollama.

## Command-line interface (F01–F03)

With the backend running, run from the repository root using Java 17+:

```powershell
java cli/StudyPlannerCli.java help
java cli/StudyPlannerCli.java list
java cli/StudyPlannerCli.java create "EECS 3311" "Software design" 2026-09-01 2026-12-20
java cli/StudyPlannerCli.java materials COURSE_ID
java cli/StudyPlannerCli.java upload COURSE_ID documentation/samples/f02-notes.txt
java cli/StudyPlannerCli.java content COURSE_ID MATERIAL_ID
java cli/StudyPlannerCli.java summarize COURSE_ID MATERIAL_ID
java cli/StudyPlannerCli.java summary COURSE_ID MATERIAL_ID
java cli/StudyPlannerCli.java regenerate-summary COURSE_ID MATERIAL_ID
```

The CLI prompts securely for the password in an interactive terminal. Set
`STUDY_USERNAME` for a username other than `student`. Automation can supply
`STUDY_PASSWORD` through its environment. `BACKEND_URL` overrides the API address.
Do not put passwords in CLI arguments or committed scripts. API failures print an
error and exit with code 1. CLI and GUI use the same validation, ownership, and storage.

## Local configuration and verification

`backend/config.local.properties.example` contains placeholders only. Its local
copy and all `.env*` files (except `.env.example`) are ignored by Git. Maven and
Spring Boot do not automatically load `.env.local`. F01/F02 do not need a model;
F03/F04 use your configured local Ollama model. Backend-only environment overrides
are `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`, and `LLM_TOKEN_LIMIT_FIELD`.

```powershell
cd backend
.\mvnw.cmd clean verify
# In another terminal, from frontend/:
.\mvnw.cmd package -Pproduction
```

The living [Stage 2 report (PDF)](documentation/reports/stage2-report.pdf) and its
[editable source](documentation/reports/stage2-report.html) track F01–F06 and UC-11,
remaining requirements, architecture changes, verification, and demonstration work.

For optional end-to-end verification on Windows, build both packages above, then
run from the repository root with Node.js, Chrome, and `JAVA_HOME` configured:

```powershell
npm.cmd install --prefix frontend/target/browser-tools --no-package-lock --no-audit --no-fund playwright-core
.\scripts\verify-f01.ps1
# Include course navigation, upload, preview, and material persistence checks:
.\scripts\verify-f01.ps1 -IncludeF02
# Add F03 protocol/UI checks using a scripted test provider (not a real model):
.\scripts\verify-f01.ps1 -IncludeF03
# Use the actual running local Ollama model and a safe sample document:
.\scripts\verify-f03-live.ps1
```

This uses temporary random credentials, a separate file database under `backend/target/`,
and ports 8090/8091. It checks GUI, CLI, API failures, and persistence after restart,
then stops its own servers. `CHROME_PATH` overrides the browser executable location.
The temporary browser dependency is not part of either application's runtime.
F03 fixture checks also use port 8092. The fixture is confined to `scripts/fixtures/`
and is never a production fallback. They test integration, not live model quality.

## F04 — Concept extraction

In an open course, choose **Extract Concepts** under a learning material. The list
shows important concepts, explanations, and exact supporting source quotations.
**View saved concepts** reopens the saved set; **Regenerate concepts** explicitly
replaces it after successful generation. Failed regeneration preserves the old set.
The agent extracts source sections and consolidates longer documents, using the
same local Ollama configuration as F03. It accepts 40–32,000 characters and returns
up to 10 concepts. Concept names may paraphrase the material; quotations are resolved from source-excerpt IDs. Normalized duplicate names are merged; semantic duplicates and
inaccurate explanations can still occur, so review the source.

```powershell
java cli/StudyPlannerCli.java extract-concepts COURSE_ID MATERIAL_ID
java cli/StudyPlannerCli.java concepts COURSE_ID MATERIAL_ID
java cli/StudyPlannerCli.java regenerate-concepts COURSE_ID MATERIAL_ID
# Scripted integration checks, including F01–F03 regression:
.\scripts\verify-f01.ps1 -IncludeF04
# Real Ollama verification with safe samples, including multi-section extraction:
.\scripts\verify-f03-live.ps1 -IncludeF04
```

Extraction targets one uploaded document (or a section uploaded as its own file).
It does not add course-section management or whole-course extraction. See the
[F04 UML and sequence](documentation/diagrams/stage2-f04.md) for the implemented flow.
## F05 — Questions about course materials

Open a course and use **Course chat**. Enter a question and click **Send question**.
Answers use excerpts retrieved from that course's uploaded text. Expand a source
to read its quotation or select **View source text** to inspect the document.
**Refresh chat** reloads saved exchanges; **Load older messages** loads history
before the displayed page. Failed requests preserve your input and saved messages.
You can also ask "Tell me about this course" for an overview based on a selection of uploaded passages. Specific questions with no matching passages are declined without a model call. The model can also decline when matching excerpts do not answer the question.

```powershell
java cli/StudyPlannerCli.java ask COURSE_ID "How does active transport differ from diffusion?"
java cli/StudyPlannerCli.java history COURSE_ID
java cli/StudyPlannerCli.java history COURSE_ID BEFORE_SEQUENCE
.\scripts\verify-f01.ps1 -IncludeF05
.\scripts\verify-f05-live.ps1
```

F05 uses the existing backend Ollama configuration. Questions allow 1000 characters;
chat searches up to 50 files and 1,000,000 extracted characters per course. It sends up to
six matching passages and short excerpts from two recent exchanges to the model. Word-based
retrieval can miss synonyms or relevant information; include topic names in follow-ups.
Source quotations identify the supplied text but do not guarantee factual accuracy.
See [F05 classes and sequence](documentation/diagrams/stage2-f05.md).
Chat answers that stop at an introduction or empty list item get one correction attempt before saving. To verify the reported assignment-PDF question with real Ollama, run scripts/verify-f05-completeness.ps1.

## F06 — Personalized study plans

Open a course and expand **Study plan and availability**:

1. Save your **study profile**: weekly study minutes, time zone, preferences, habits,
   and weekly availability windows. This profile is shared across your courses.
2. Add any **BUSY**, **EXAM**, or **QUIZ** events. GUI event times use your saved
   profile's time zone. The API/CLI accept UTC timestamps.
3. Choose the plan dates, session duration, and number of sessions. Optionally enter
   previous assessment results or topics you find difficult, then click **Generate study plan**.
4. Open **Saved study plan** to see priorities, scheduled sessions, activities, and sources.

The AI chooses topics and activities; Java schedules the times. Sessions avoid all
your stored events, share one weekly budget across courses, and finish before the
first upcoming quiz/exam in the chosen period. Past exams are not deadlines for new
sessions. Regeneration replaces this course's old study events through the Observer
pattern. Failed generation keeps the previous plan and calendar. Profile/calendar
changes take effect on the next generation. Saved data remains readable with Ollama offline.

```powershell
java cli/StudyPlannerCli.java save-profile documentation/samples/study-profile.json
java cli/StudyPlannerCli.java profile
# Edit the sample plan dates to fit your course and be today or later:
java cli/StudyPlannerCli.java generate-plan COURSE_ID documentation/samples/study-plan-request.json
java cli/StudyPlannerCli.java plan COURSE_ID
# Edit the sample event timestamp/title before adding it:
java cli/StudyPlannerCli.java add-event COURSE_ID documentation/samples/study-busy-event.json
java cli/StudyPlannerCli.java events COURSE_ID 2026-11-09T00:00:00Z 2026-11-23T00:00:00Z
java cli/StudyPlannerCli.java remove-event COURSE_ID EVENT_ID
.\scripts\verify-f01.ps1 -IncludeF06
.\scripts\verify-f06-live.ps1
```

Limits: 1–30 sessions, 15–180 minutes each in 15-minute increments, over 1–56 days
within the course dates. Weekly availability windows cannot overlap. Plans use up
to 50 materials / 1,000,000 extracted characters, with six source excerpts and at
most two model calls. Review AI advice against the source; citations do not prove
recommendation quality. No additional AI configuration or dependency is needed.

F06 includes UC-11 profile maintenance and the calendar support needed for scheduling.
The full F10 calendar interface and automatic quiz-result integration remain pending;
assessment notes are entered manually until F08/F09 exist. See the
[F06 UML and sequence](documentation/diagrams/stage2-f06.md).
## Flashcards (F07)

Open a course and expand **Flashcards**. Choose a maximum of 1–20 cards, optionally
select materials and a topic, then click **Generate flashcards**. Leaving materials
empty uses all course documents. Select a separately uploaded section to study it.
The model may return fewer cards when the excerpts support fewer distinct questions.

Click the question or **Show answer** to reveal the answer and source quotations.
**Previous card** / **Next card** reset the answer to hidden. **Saved decks** reopens
older results without a model call; generation failures preserve existing decks.
Answers wrap at phone widths. Review AI answers against their sources.

The flow follows Stage 1 SD04: Course → StudyAssistantFacade → AssessmentAgent →
AIEngine → StudyResourceFactory → FlashcardDeck. It reuses Facade, Singleton, and
Simple Factory. See [F07 diagrams and limits](documentation/diagrams/stage2-f07.md).

```powershell
java cli/StudyPlannerCli.java flashcards COURSE_ID documentation/samples/flashcard-request.json
java cli/StudyPlannerCli.java decks COURSE_ID
java cli/StudyPlannerCli.java deck COURSE_ID DECK_ID
```

Use the existing CLI credentials and BACKEND_URL settings. The sample uses all
materials with a five-card maximum. `materialIds` accepts uploaded material UUIDs;
`focus` narrows the topic. Tests: `scripts/verify-f01.ps1 -IncludeF07` uses a scripted
protocol fixture; `scripts/verify-f07-live.ps1` uses real local Ollama. Both require
packaged apps and the existing Chrome/Playwright verification setup.
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
