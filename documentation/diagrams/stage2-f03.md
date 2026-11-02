# Stage 2 - F03 Document Summarization

References: F03, UC-03, SD02 summary branch, Stage 1 Task 4 F03.
Only implemented classes and relationships are shown. Other resource products,
agents, Strategy and Observer are future work.

```mermaid
classDiagram
    MainView --> CourseMaterialsPanel
    CourseMaterialsPanel --> SummaryActions
    SummaryActions --> CourseApi : background request
    StudyPlannerCli --> SummaryController : REST
    CourseApi --> SummaryController : authenticated REST
    SummaryController --> SummaryService
    SummaryService --> MaterialService : ownership and source lookup
    SummaryService --> Course : generateStudyResource
    Course --> StudyAssistantFacade : summarize
    StudyAssistantFacade --> ContentAgent : plan and validate
    StudyAssistantFacade --> StudyResourceFactory : createResource
    ContentAgent --> LearningMaterial : getContent
    ContentAgent --> AIEngine : generate
    ContentAgent --> AiSettings : immutable provider settings
    StudyResourceFactory --> Summary : constructs identity and provenance
    StudyResource <|-- Summary
    SummaryService --> SummaryRepository
    SummaryRepository <|.. JdbcSummaryRepository
    JdbcSummaryRepository --> Summary : save and retrieve
    class StudyAssistantFacade {
        <<Facade>>
        summarize(material) Summary
    }
    class ContentAgent {
        summarize(material) Draft
        split(source)
        generateValidated()
    }
    class AIEngine {
        <<Singleton>>
        -AIEngine()
        +getInstance()$ AIEngine
        +generate(context, instruction, settings) Completion
    }
    class StudyResourceFactory {
        <<Simple Factory>>
        createResource(type, material, draft) StudyResource
    }
    class StudyResource {
        <<abstract>>
        UUID resourceId
        UUID courseId
        UUID materialId
        Instant createdAt
        view()* String
    }
    class Summary {
        String summaryText
        List evidence
        String model
        String sourceHash
        view() String
    }
    class SummaryRepository {
        <<interface>>
        find(courseId, materialId)
        save(summary)
    }
```

```mermaid
sequenceDiagram
    actor Student
    participant UI as SummaryActions / CLI
    participant Controller as SummaryController
    participant Service as SummaryService
    participant Material as MaterialService / LearningMaterial
    participant Course
    participant Facade as StudyAssistantFacade
    participant Agent as ContentAgent
    participant Engine as AIEngine singleton
    participant LLM as Local Ollama / compatible API
    participant Factory as StudyResourceFactory
    participant DB as SummaryRepository / H2
    Student->>UI: Summarize document
    UI->>Controller: POST summary (authenticated + CSRF)
    Controller->>Service: summarize(username, source IDs, regenerate)
    Service->>Material: Verify ownership and retrieve source
    Material-->>Service: LearningMaterial
    Service->>DB: Find saved summary
    alt Cached source matches and regeneration not requested
        DB-->>UI: Saved Summary, no model call
    else New generation
        Service->>Course: generateStudyResource(material, facade)
        Course->>Facade: summarize(material)
        Facade->>Agent: summarize(material)
        Agent->>Material: getContent()
        Agent->>Agent: Validate size and plan sections
        loop Each section, then final synthesis if needed
            Agent->>Engine: generate(context, instruction, settings)
            Engine->>LLM: Schema-constrained JSON chat completion
            LLM-->>Engine: Generated content and optional token usage
            Engine-->>Agent: Completion
            Agent->>Agent: Validate JSON and resolve evidence IDs to exact source excerpts
            opt Invalid structured output
                Agent->>Engine: One repair attempt
                Engine->>LLM: Retry with schema/excerpt-ID instructions
                LLM-->>Agent: Replacement content
            end
        end
        Agent-->>Facade: Validated Draft and usage
        Facade->>Factory: createResource(Summary, material, draft)
        Factory-->>Facade: Summary with ID, timestamp and source hash
        Facade-->>Service: Summary
        Service->>DB: Atomically save latest summary
        DB-->>UI: Summary
    end
    UI-->>Student: Summary and supporting quotations
```

Invalid ownership, insufficient/oversized content, missing configuration, rate
limits, provider failures, malformed/ungrounded output and timeouts produce safe
errors. Failed regeneration preserves the previous saved summary.

Changes from Stage 1: ContentAgent adds bounded section planning, synthesis and
indexed source-excerpt validation. The model chooses excerpt IDs instead of
transcribing quotations; the backend resolves and verifies the exact source text.
AIEngine's static singleton shares connections/concurrency
limits while accepting immutable provider settings per call. The simple Factory
currently constructs only Summary; no unimplemented product is claimed. JDBC
adds persistent summary memory. Direct Java HTTP replaces the proposed LangChain
integration for this feature; retrieval/RAG for F05 remains future work.
