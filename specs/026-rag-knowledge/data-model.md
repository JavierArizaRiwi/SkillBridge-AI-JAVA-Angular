# Data model: HU-26 Asistente RAG

Cambio material de persistencia: una tabla nueva con una columna `vector`. Se referencia desde `research.md` (R1, R3, R4).

## Entidad de dominio: `KnowledgeChunk` (pura Java, sin anotaciones)

| Campo | Tipo | Regla |
|---|---|---|
| `sourceId` | String | Identificador único del fragmento. Oferta: `offering:<id>`. Categoría: `category:<id>`. Política: el `sourceId` declarado en el `.md` (p. ej. `cancellation-policy`). |
| `sourceType` | enum `OFFERING`, `CATEGORY`, `POLICY_DOC` | Obligatorio (spec, Key Entities). |
| `title` | String | No vacío. |
| `content` | String | Texto indexado. Solo público (FR-004). |
| `language` | enum `ES`, `EN`, `MIXED` | Los `.md` de política se indexan por sección de idioma (FR-014). Las ofertas son `ES` hoy. |
| `contentHash` | String | Hash del `content`. Solo se recalcula el embedding si cambia (FR-018). |
| `mode`, `durationHours` | String, Integer (nullable) | Solo para ofertas (FR-004.4). |

Invariantes:
- Un fragmento con `visibility` distinta de `public` no se crea nunca (FR-004; el indexador descarta el archivo).
- `contentHash` igual al anterior → no se recalcula el embedding.

## Persistencia: schema `knowledge`, tabla `knowledge_chunks` (migración `V14__knowledge_chunks.sql`)

Decisión (2026-11-10): schema dedicado dentro de la misma base `skillbridge`, sin base nueva (ADR-0006: el RAG pertenece al monolito; una base aparte obligaría a copiar ofertas y categorías).

```sql
-- La extensión es por base de datos: se crea en public para que el tipo vector sea visible desde cualquier schema.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE SCHEMA IF NOT EXISTS knowledge;

CREATE TABLE knowledge.knowledge_chunks (
    id             UUID PRIMARY KEY,
    source_id      VARCHAR(120) NOT NULL UNIQUE,
    source_type    VARCHAR(20)  NOT NULL,
    title          VARCHAR(300) NOT NULL,
    content        TEXT         NOT NULL,
    language       VARCHAR(5)   NOT NULL,
    content_hash   VARCHAR(64)  NOT NULL,
    mode           VARCHAR(20),
    duration_hours INTEGER,
    embedding      vector(768)  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX knowledge_chunks_embedding_hnsw
    ON knowledge.knowledge_chunks USING hnsw (embedding vector_cosine_ops);
```

Notas:
- Los nombres de tabla van calificados (`knowledge.knowledge_chunks`) en SQL nativo y en la entidad JPA (`@Table(schema = "knowledge")`). Así no depende de `search_path` ni de `currentSchema` del datasource, que hoy no están configurados (`application.yml`).
- El historial de Flyway sigue en `public`, igual que las migraciones V1–V13.
- `CREATE EXTENSION` requiere privilegio sobre la base. En Testcontainers la base es propia; en Railway se ejecuta una vez con el usuario de la base.
- `vector(768)` coincide con `output_dimensionality` de R1. Cambiar de modelo o de dimensión exige recalcular todos los vectores (FR-018) y una migración nueva.
- El índice HNSW con `vector_cosine_ops` coincide con la métrica de similitud coseno de FR-005. Con ~80 fragmentos la búsqueda exacta bastaría; el índice se crea porque es parte del aprendizaje y se verifica en T5 con `EXPLAIN`.
- No hay tabla de consultas ni de logs: la spec prohíbe guardar el texto completo de la pregunta (FR-010).

## Índice en memoria BM25

No es una tabla. Se construye al arrancar y se reconstruye al invalidar la caché de ofertas o categorías (FR-015). Es una estructura de solo lectura con reemplazo atómico (una referencia `volatile` a un índice nuevo) para que las lecturas concurrentes nunca vean un índice a medias (edge case de concurrencia).

## Relaciones

- `knowledge_chunks` no referencia `offerings` ni `categories`. Son copias de lectura. Así el RAG no consulta otros módulos en tiempo de consulta (ADR-0006).
