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
