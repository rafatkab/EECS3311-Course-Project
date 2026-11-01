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
extraction are deterministic; AI resource generation is not implemented yet.

## Command-line interface (F01 and F02)

With the backend running, run from the repository root using Java 17+:

```powershell
java cli/StudyPlannerCli.java help
java cli/StudyPlannerCli.java list
java cli/StudyPlannerCli.java create "EECS 3311" "Software design" 2026-09-01 2026-12-20
java cli/StudyPlannerCli.java materials COURSE_ID
java cli/StudyPlannerCli.java upload COURSE_ID documentation/samples/f02-notes.txt
java cli/StudyPlannerCli.java content COURSE_ID MATERIAL_ID
```

The CLI prompts securely for the password in an interactive terminal. Set
`STUDY_USERNAME` for a username other than `student`. Automation can supply
`STUDY_PASSWORD` through its environment. `BACKEND_URL` overrides the API address.
Do not put passwords in CLI arguments or committed scripts. API failures print an
error and exit with code 1. CLI and GUI use the same validation, ownership, and storage.

## Local configuration and verification

`backend/config.local.properties.example` contains placeholders only. Its local
copy and all `.env*` files (except `.env.example`) are ignored by Git. Maven and
Spring Boot do not automatically load `.env.local`. No LLM key is needed for F01;
LLM integration belongs to later features and is not implemented yet.

```powershell
cd backend
.\mvnw.cmd clean verify
# In another terminal, from frontend/:
.\mvnw.cmd package -Pproduction
```

The living [Stage 2 report (PDF)](documentation/reports/stage2-report.pdf) and its
[editable source](documentation/reports/stage2-report.html) track F01–F02,
remaining requirements, architecture changes, verification, and demonstration work.

For optional end-to-end verification on Windows, build both packages above, then
run from the repository root with Node.js, Chrome, and `JAVA_HOME` configured:

```powershell
npm.cmd install --prefix frontend/target/browser-tools --no-package-lock --no-audit --no-fund playwright-core
.\scripts\verify-f01.ps1
# Include course navigation, upload, preview, and material persistence checks:
.\scripts\verify-f01.ps1 -IncludeF02
```

This uses temporary random credentials, a separate file database under `backend/target/`,
and ports 8090/8091. It checks GUI, CLI, API failures, and persistence after restart,
then stops its own servers. `CHROME_PATH` overrides the browser executable location.
The temporary browser dependency is not part of either application's runtime.
