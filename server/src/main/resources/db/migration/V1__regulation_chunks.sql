-- Citable chunks of the DORA regulation and the register templates, with their embeddings.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE regulation_chunks (
    id              BIGSERIAL PRIMARY KEY,
    citation_id     TEXT         NOT NULL UNIQUE,
    regulation      TEXT         NOT NULL,
    article         TEXT         NOT NULL,
    paragraph       TEXT,
    text            TEXT         NOT NULL,
    page            INTEGER      NOT NULL,
    -- SHA-256 of embedding model id plus chunk text: a new model or new text means a new row.
    content_hash    CHAR(64)     NOT NULL UNIQUE,
    embedding_model TEXT         NOT NULL,
    embedding       VECTOR(1536) NOT NULL
);

CREATE INDEX regulation_chunks_embedding_idx
    ON regulation_chunks USING hnsw (embedding vector_cosine_ops);
