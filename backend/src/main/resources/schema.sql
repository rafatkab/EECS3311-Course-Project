CREATE TABLE IF NOT EXISTS courses (
    course_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT valid_course_dates CHECK (end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS courses_owner ON courses(owner_id);

CREATE TABLE IF NOT EXISTS learning_materials (
    material_id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(course_id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    upload_date TIMESTAMP WITH TIME ZONE NOT NULL,
    extracted_content CLOB NOT NULL,
    original_file BLOB NOT NULL
);
CREATE INDEX IF NOT EXISTS materials_course ON learning_materials(course_id);

CREATE TABLE IF NOT EXISTS summaries (
    resource_id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(course_id) ON DELETE CASCADE,
    material_id UUID NOT NULL UNIQUE REFERENCES learning_materials(material_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    summary_text CLOB NOT NULL,
    evidence CLOB NOT NULL,
    model VARCHAR(255) NOT NULL,
    source_characters INT NOT NULL,
    sections INT NOT NULL,
    model_calls INT NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    source_hash CHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS concept_sets (
    resource_id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(course_id) ON DELETE CASCADE,
    material_id UUID NOT NULL UNIQUE REFERENCES learning_materials(material_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    concepts CLOB NOT NULL,
    model VARCHAR(255) NOT NULL,
    source_characters INT NOT NULL,
    sections INT NOT NULL,
    model_calls INT NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    source_hash CHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS course_chat_turns (
    turn_id UUID PRIMARY KEY,
    sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    course_id UUID NOT NULL REFERENCES courses(course_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    question VARCHAR(1000) NOT NULL,
    answer CLOB NOT NULL,
    answerable BOOLEAN NOT NULL,
    citations CLOB NOT NULL,
    model VARCHAR(255) NOT NULL,
    model_calls INT NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS chat_course_sequence ON course_chat_turns(course_id, sequence);

CREATE TABLE IF NOT EXISTS study_profiles (
    owner_id UUID PRIMARY KEY,
    profile_json CLOB NOT NULL
);
CREATE TABLE IF NOT EXISTS study_plans (
    resource_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    course_id UUID NOT NULL UNIQUE REFERENCES courses(course_id) ON DELETE CASCADE,
    plan_json CLOB NOT NULL
);
CREATE TABLE IF NOT EXISTS calendar_events (
    event_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    course_id UUID NOT NULL REFERENCES courses(course_id) ON DELETE CASCADE,
    plan_id UUID REFERENCES study_plans(resource_id) ON DELETE CASCADE,
    type VARCHAR(12) NOT NULL,
    title VARCHAR(120) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT valid_event_times CHECK (end_time > start_time)
);
CREATE INDEX IF NOT EXISTS calendar_owner_time ON calendar_events(owner_id, start_time, end_time);

CREATE TABLE IF NOT EXISTS flashcard_decks (
    resource_id UUID PRIMARY KEY,
    sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    course_id UUID NOT NULL REFERENCES courses(course_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    card_limit INT NOT NULL,
    card_count INT NOT NULL,
    focus VARCHAR(200) NOT NULL,
    deck_json CLOB NOT NULL,
    CONSTRAINT valid_flashcard_count CHECK (card_count >= 1 AND card_count <= card_limit AND card_limit <= 20)
);
CREATE INDEX IF NOT EXISTS flashcards_course_sequence ON flashcard_decks(course_id, sequence);

CREATE TABLE IF NOT EXISTS quizzes (
    resource_id UUID PRIMARY KEY,
    sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    course_id UUID NOT NULL REFERENCES courses(course_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    question_count INT NOT NULL CHECK (question_count BETWEEN 1 AND 20),
    difficulty VARCHAR(10) NOT NULL,
    topics VARCHAR(500) NOT NULL,
    quiz_json CLOB NOT NULL
);
CREATE INDEX IF NOT EXISTS quizzes_course_sequence ON quizzes(course_id,sequence);
CREATE TABLE IF NOT EXISTS quiz_attempts (
    attempt_id UUID PRIMARY KEY,
    sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    quiz_id UUID NOT NULL REFERENCES quizzes(resource_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    version INT NOT NULL,
    responses_json CLOB NOT NULL
);
CREATE INDEX IF NOT EXISTS quiz_attempts_quiz_sequence ON quiz_attempts(quiz_id,sequence);
