CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE kb_piece (
    piece_id text PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE kb_piece_version (
    piece_id text REFERENCES kb_piece,
    version integer CHECK (version > 0),
    title text NOT NULL,
    type text NOT NULL,
    family text NOT NULL,
    audience text NOT NULL CHECK (audience IN ('publico', 'interno')),
    owner_area text NOT NULL,
    language text NOT NULL DEFAULT 'es' CHECK (language = 'es'),
    status text NOT NULL CHECK (status IN ('propuesta', 'borrador', 'en_revision', 'aprobada', 'publicada', 'retirada')),
    body_template text NOT NULL,
    source_refs jsonb NOT NULL,
    content_hash text NOT NULL,
    valid_from timestamptz NOT NULL,
    valid_to timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (piece_id, version),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE TABLE kb_parameter_version (
    name text,
    version integer CHECK (version > 0),
    value text NOT NULL,
    is_test boolean NOT NULL,
    status text NOT NULL,
    approved_at timestamptz,
    PRIMARY KEY (name, version)
);

CREATE TABLE kb_approval (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_kind text NOT NULL DEFAULT 'piece',
    piece_id text,
    parameter_name text,
    version integer NOT NULL,
    role text NOT NULL,
    approved_at timestamptz NOT NULL DEFAULT now(),
    is_test boolean NOT NULL,
    note text,
    FOREIGN KEY (piece_id, version) REFERENCES kb_piece_version,
    FOREIGN KEY (parameter_name, version) REFERENCES kb_parameter_version,
    CHECK (
        (subject_kind = 'piece' AND piece_id IS NOT NULL AND parameter_name IS NULL)
        OR (subject_kind = 'parameter' AND parameter_name IS NOT NULL AND piece_id IS NULL)
    )
);

CREATE TABLE kb_release (
    release_no serial PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    reason text NOT NULL CHECK (reason IN ('publish', 'retire', 'rollback', 'sync', 'expire')),
    parent_release_no integer REFERENCES kb_release,
    is_active boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX kb_release_one_active ON kb_release (is_active) WHERE is_active;

CREATE TABLE kb_release_item (
    release_no integer REFERENCES kb_release,
    piece_id text,
    version integer NOT NULL,
    rendered_hash text NOT NULL,
    PRIMARY KEY (release_no, piece_id),
    FOREIGN KEY (piece_id, version) REFERENCES kb_piece_version
);

CREATE TABLE kb_chunk (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    piece_id text NOT NULL,
    version integer NOT NULL,
    rendered_hash text NOT NULL,
    chunk_no integer NOT NULL CHECK (chunk_no >= 0),
    section text,
    title text NOT NULL,
    text text NOT NULL,
    embedding vector(768) NOT NULL,
    tsv tsvector GENERATED ALWAYS AS (to_tsvector('spanish', title || ' ' || text)) STORED,
    embedding_model text NOT NULL,
    family text NOT NULL,
    type text NOT NULL,
    audience text NOT NULL CHECK (audience IN ('publico', 'interno')),
    FOREIGN KEY (piece_id, version) REFERENCES kb_piece_version,
    UNIQUE (piece_id, version, rendered_hash, chunk_no)
);

CREATE INDEX kb_chunk_embedding_hnsw ON kb_chunk USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
CREATE INDEX kb_chunk_tsv_gin ON kb_chunk USING gin (tsv);
CREATE INDEX kb_chunk_filters ON kb_chunk (family, type, audience);
CREATE INDEX kb_chunk_rendered ON kb_chunk (piece_id, version, rendered_hash);

CREATE TABLE kb_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at timestamptz NOT NULL DEFAULT now(),
    action text NOT NULL CHECK (action IN ('ingest', 'reject', 'submit', 'approve', 'publish', 'retire', 'rollback', 'sync')),
    subject text NOT NULL,
    version integer,
    role text,
    release_no integer REFERENCES kb_release,
    detail jsonb NOT NULL DEFAULT '{}'
);

CREATE TABLE kb_query_log (
    at timestamptz NOT NULL DEFAULT now(),
    correlation_id text NOT NULL,
    consumer_id text NOT NULL,
    release_no integer REFERENCES kb_release,
    cited jsonb NOT NULL,
    level text NOT NULL CHECK (level IN ('alta', 'baja', 'sin_respuesta')),
    top_score double precision,
    latency_ms double precision NOT NULL CHECK (latency_ms >= 0),
    query_len integer NOT NULL CHECK (query_len >= 0)
);
