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
