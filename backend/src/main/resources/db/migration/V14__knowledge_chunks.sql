-- La extensión es por base de datos: se crea en public para que el tipo vector sea visible desde cualquier schema.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE SCHEMA IF NOT EXISTS knowledge;

CREATE TABLE knowledge.knowledge_chunks (
    id             UUID PRIMARY KEY,
    source_id      VARCHAR(120) NOT NULL,
    source_type    VARCHAR(20)  NOT NULL,
    title          VARCHAR(300) NOT NULL,
    content        TEXT         NOT NULL,
    language       VARCHAR(5)   NOT NULL,
    content_hash   VARCHAR(64)  NOT NULL,
    mode           VARCHAR(20),
    duration_hours INTEGER,
    embedding      vector(768)  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    -- Un documento del corpus produce un fragmento por idioma (ES/EN) con el mismo source_id.
    CONSTRAINT knowledge_chunks_source_lang_uk UNIQUE (source_id, language)
);

CREATE INDEX knowledge_chunks_embedding_hnsw
    ON knowledge.knowledge_chunks USING hnsw (embedding vector_cosine_ops);
