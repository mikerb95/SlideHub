-- V8: add Q&A configuration columns to presentations
-- questionsEnabled: allows disabling Q&A entirely per presentation
-- allowAnonymousQuestions: controls whether viewers can submit without a display name

ALTER TABLE presentations
    ADD COLUMN IF NOT EXISTS questions_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allow_anonymous_questions BOOLEAN NOT NULL DEFAULT FALSE;
