# Stage 2 — F01 implemented diagrams

These diagrams supplement the original UML/SD01. F02's upload branch is not yet implemented.

```mermaid
classDiagram
    MainView --> CourseApi : GUI events
    CourseApiFactory --> CourseApi : per-view HTTP session
    CourseApi --> CourseController : HTTP Basic + CSRF
    StudyPlannerCli --> CourseController : same REST API
    CourseController --> CourseService
    CourseService --> User : authenticated principal
    User --> Course : creates owned course
    CourseService --> CourseRepository
    User --> CourseRepository : viewCourses
    CourseRepository <|.. JdbcCourseRepository
    JdbcCourseRepository --> Course : persists/maps
    class User {
        UUID userId
        String name
        createCourse()
        viewCourses()
    }
    class Course {
        UUID courseId
        UUID ownerId
        String name
        String description
        LocalDate startDate
        LocalDate endDate
        Instant createdAt
    }
    class CourseRepository {
        <<interface>>
        save()
        findByOwner()
        findByIdAndOwner()
    }
```

```mermaid
sequenceDiagram
    actor Student
    participant UI as MainView / CLI
    participant API as CourseApi / HttpClient
    participant Security as Spring Security
    participant Controller as CourseController
    participant Service as CourseService
    participant User
    participant DB as JdbcCourseRepository / H2
    Student->>UI: Create Course, enter four fields
    UI->>UI: Required fields and date validation (GUI)
    UI->>API: createCourse(request)
    API->>Security: GET /api/csrf (Basic)
    Security-->>API: CSRF token and session cookie
    API->>Security: POST /api/courses (Basic, cookie, CSRF)
    alt Missing/invalid authentication or CSRF
        Security-->>UI: 401 / 403, display error
    else Authorized request
        Security->>Controller: Authenticated principal and request
        Controller->>Controller: Bean Validation
        Controller->>Service: createCourse(username, request)
        Service->>User: createCourse(name, description, dates)
        User->>User: Construct Course and enforce invariants
        alt Missing/invalid fields or dates
            User-->>UI: 400 validation error, no insert
        else Valid course
            Service->>DB: save(course), transaction
            alt Storage failure
                DB-->>UI: 503 safe error, no fake success
            else Saved
                DB-->>Controller: Saved Course
                Controller-->>API: 201, Course, Location
                API-->>UI: Saved Course
                UI->>Controller: GET /api/courses (authenticated)
                Controller->>Service: viewCourses(username)
                Service->>User: viewCourses(repository)
                User->>DB: findByOwner(userId)
                DB-->>UI: Owned courses
                UI-->>Student: Updated dashboard / CLI JSON
            end
        end
    end
```

```mermaid
flowchart LR
    Student --> SignIn[Authentication precondition]
    SignIn --> UC01[UC-01: create and view owned courses]
    UC01 --> GUI[Vaadin dashboard and form]
    UC01 --> CLI[Java command-line create and list]
    GUI --> API[Authenticated course REST API]
    CLI --> API
    API --> Store[Persistent owned courses]
```

Changes from Stage 1: explicit HTTP client, security, service, and repository reflect
the separate runtime processes and durable storage. User.createCourse/viewCourses
remain meaningful domain operations. No AI design pattern is added for F01.
