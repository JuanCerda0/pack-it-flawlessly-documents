CREATE SEQUENCE documents_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE documents (
    id BIGINT PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    package_id BIGINT NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    document_number VARCHAR(40) NOT NULL UNIQUE,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    generated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    package_snapshot JSONB NOT NULL,
    pdf_data BYTEA NOT NULL
);

CREATE INDEX idx_documents_package_generated
    ON documents (package_id, generated_at, id);
