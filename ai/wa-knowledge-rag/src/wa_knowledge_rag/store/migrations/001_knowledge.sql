CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE kb_source (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    owner_area TEXT NOT NULL,
    audience TEXT NOT NULL CHECK (audience IN ('public', 'internal')),
    version INTEGER NOT NULL CHECK (version > 0),
    approved_by TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('draft', 'approved', 'retired')),
    source_uri TEXT,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (title, version)
);

CREATE TABLE kb_chunk (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES kb_source(id) ON DELETE RESTRICT,
    version INTEGER NOT NULL CHECK (version > 0),
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    content TEXT NOT NULL,
    section TEXT,
    metadata JSONB NOT NULL DEFAULT '{}',
    embedding vector(768),
    tsv tsvector,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, version, ordinal)
);

CREATE INDEX idx_kb_chunk_embedding_hnsw ON kb_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_kb_chunk_tsv_gin ON kb_chunk USING gin (tsv);
CREATE INDEX idx_kb_chunk_source_version ON kb_chunk (source_id, version);
CREATE INDEX idx_kb_source_audience_status ON kb_source (audience, status);

CREATE FUNCTION kb_chunk_update_tsv() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.tsv := to_tsvector('spanish', NEW.content);
    RETURN NEW;
END;
$$;

CREATE TRIGGER kb_chunk_tsv BEFORE INSERT OR UPDATE ON kb_chunk
FOR EACH ROW EXECUTE FUNCTION kb_chunk_update_tsv();
