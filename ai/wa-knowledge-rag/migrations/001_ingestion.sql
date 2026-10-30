-- Aplicar con psql -v ON_ERROR_STOP=1 -f migrations/001_ingestion.sql
BEGIN;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE kb_source (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL,
    title TEXT NOT NULL,
    owner_area TEXT NOT NULL,
    audience TEXT NOT NULL CHECK (audience IN ('public', 'internal')),
    version INTEGER NOT NULL CHECK (version > 0),
    approved_by TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('draft', 'approved', 'retired')),
    UNIQUE (source_id, version)
);
CREATE UNIQUE INDEX kb_source_current ON kb_source(source_id) WHERE status = 'approved';
CREATE TABLE kb_chunk (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES kb_source(id),
    version INTEGER NOT NULL,
    ordinal INTEGER NOT NULL,
    content TEXT NOT NULL,
    section TEXT NOT NULL,
    metadata JSONB NOT NULL,
    embedding vector(768) NOT NULL,
    tsv tsvector NOT NULL,
    UNIQUE (source_id, version, ordinal)
);
CREATE INDEX kb_chunk_vector ON kb_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX kb_chunk_text ON kb_chunk USING gin(tsv);
CREATE INDEX kb_chunk_source_version ON kb_chunk(source_id, version);
COMMIT;
