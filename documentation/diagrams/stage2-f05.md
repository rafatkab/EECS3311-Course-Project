# Stage 2 — F05 Questions About Course Materials

References: Stage 1 F05, UC-05, Task 4 F05, UML-Diagram.uxf, and SD02 question branch.

```mermaid
classDiagram
    CourseMaterialsPanel --> CourseChatPanel
    CourseChatPanel --> CourseApi : background requests
    CourseApi --> ChatController : authenticated REST
    StudyPlannerCli --> ChatController : same REST API
    ChatController --> ChatService
    ChatService --> CourseService : ownership check
    ChatService --> MaterialRepository : selected course only
    ChatService --> Course : askQuestion
    Course --> CourseChat : askQuestion
    CourseChat --> StudyAssistantFacade : answerQuestion
    StudyAssistantFacade --> ContentAgent : answerQuestion
    ContentAgent --> CourseContextRetriever : select relevant excerpts
    ContentAgent --> AIEngine : generate
    ContentAgent --> ChatAnswerValidator : isUnfinished
    CourseChat --> ChatTurn : viewHistory
    ChatService --> ChatRepository : save completed turn / history
    ChatRepository <|.. JdbcChatRepository
    ChatTurn *-- Citation
    class CourseChat {
        Course course
        List~ChatTurn~ history
        askQuestion(question, materials, assistant) AnswerDraft
        viewHistory() List
    }
    class StudyAssistantFacade {
        <<Facade>>
        answerQuestion(course, question, materials, history) AnswerDraft
    }
    class ContentAgent {
        answerQuestion(course, question, materials, history) AnswerDraft
    }
    class AIEngine {
        <<Singleton>>
        -AIEngine()
        getInstance()$ AIEngine
        generate(context, instruction, settings, timeout, schema) Completion
    }
    class ChatTurn {
        UUID turnId
        long sequence
        String question
        String answer
        boolean answerable
        List~Citation~ citations
    }
    class Citation {
        UUID materialId
        String fileName
        int startOffset
        int endOffset
        String quote
    }
    class ChatRepository {
        <<interface>>
        save(turn) ChatTurn
        history(courseId, before) List
    }
```

```mermaid
sequenceDiagram
    actor Student
    participant UI as CourseChatPanel / CLI
    participant Service as ChatController / ChatService
    participant Course
    participant Chat as CourseChat
    participant Facade as StudyAssistantFacade
    participant Agent as ContentAgent
    participant Retrieval as CourseContextRetriever
    participant LLM as AIEngine / Ollama
    participant DB as ChatRepository / H2
    Student->>UI: Send question
    UI->>Service: POST course/{id}/chat (authenticated + CSRF)
    Service->>Service: Verify owner, question, and course limits
    Service->>DB: Load recent conversation
    Service->>Course: askQuestion(question, chat, materials, facade)
    Course->>Chat: askQuestion
    Chat->>Facade: answerQuestion
    Facade->>Agent: answerQuestion
    Agent->>Retrieval: Rank course passages for question and recent topics
    Retrieval-->>Agent: Up to 6 excerpts with source IDs and offsets
    alt Matching excerpts found
    Agent->>LLM: Question, excerpts, short recent history, answer schema
    LLM-->>Agent: Answer or insufficient-context decision, source IDs
    Agent->>Agent: Check response fields, unfinished text, and exact source IDs
    opt Invalid structured output
        Agent->>LLM: One repair request
    end
    Agent-->>Service: Valid AnswerDraft (via Facade / Chat / Course)
    else No matching excerpts
        Agent-->>Service: Insufficient-context reply, no model call (via Facade / Chat / Course)
    end
    Service->>DB: Save complete question, answer, citations, and usage
    Service-->>UI: Saved ChatTurn
    UI-->>Student: Answer and expandable sources
```

UC-05 keeps its original course chat and saved-history outcome. CourseChat is an
immutable conversation snapshot; ChatService and the repository manage persistence.
The UML's plain String return is expanded to AnswerDraft/ChatTurn to carry citations,
the insufficient-context decision, and generation metadata.

Retrieval uses weighted word matches and common plural variants across all uploaded text in the selected course,
with a smaller contribution from two recent questions for related follow-ups. Prior exchanges are omitted for new topics and after an insufficient-context response. It selects up to
six passages of up to 700 characters with overlap. Broad requests such as "Tell me about this course" use representative passages spread across the uploaded materials, without requiring topic keywords. Vague retries can also request a course overview. This is lexical retrieval followed
by generation, without embeddings, LangChain, or external search. No material from
another course is supplied. Questions allow 1–1000 characters; a course supports up
to 50 materials and 1,000,000 extracted characters. Larger courses get an explicit error.
The provider receives at most two recent turns, with bounded question/answer excerpts. Generic follow-ups may use recent question topics; new unrelated topics must match the current question. For a specific question with no matching passages, the agent saves an insufficient-context reply with model=course-search, zero model calls, and zero tokens.

History pages contain up to 50 complete turns in chronological order; `before` selects
older turns. Failed generation saves nothing, and history remains readable without
the model. An insufficient-context response is saved as a normal exchange with no
citations. Each question has at most two provider calls, each using the configured
timeout (up to 120 seconds). Requests share AIEngine's two-call concurrency limit.

Patterns: the existing Facade and Singleton. Chat answers are not factory-created
study resources. Source-ID checks establish quotation provenance, not factual truth
or citation relevance. Lexical retrieval can miss synonyms or information outside
the selected passages; users can ask a more specific question and inspect source text.

Obvious unfinished answers (a dangling introduction or empty list item) receive the same single repair attempt as invalid JSON/source IDs. ChatAnswerValidator detects these cases without imposing a blanket minimum answer length; valid short answers remain allowed. Repeated failure saves no turn. This heuristic does not prove that every requested detail was answered.
