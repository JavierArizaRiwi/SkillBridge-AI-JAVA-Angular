# Implementation Plan: HU-26 Asistente de Conocimiento con RAG (híbrido)

**Branch**: `026-rag-knowledge` (la spec nombra `feature/026-rag-knowledge`; el repo está en `develop` y no se cambia de rama desde aquí) | **Date**: 2026-11-10 | **Status**: Approved (por el propietario, 2026-11-10) | **Spec**: [spec.md](spec.md) (Approved, 2026-11-10) | **ADR**: [ADR-0008](../../docs/adr/0008-rag-retrieval-embeddings-and-vector-store.md) (Accepted)

**Input**: `specs/026-rag-knowledge/spec.md`

## Summary

Un Customer autenticado hace una pregunta en lenguaje natural sobre servicios, categorías y políticas de SkillBridge. El sistema recupera fragmentos públicos con una **búsqueda híbrida**: BM25 en memoria y similitud coseno de embeddings (Gemini `gemini-embedding-001`, 768 dimensiones) sobre pgvector, fusionados con Reciprocal Rank Fusion (k=60). Si ningún fragmento supera el umbral coseno, responde «sin información» sin llamar al LLM. Si hay contexto suficiente, el LLM generativo redacta la respuesta solo con esos fragmentos, y las fuentes devueltas son solo las citadas. Si falla el proveedor de embeddings, la respuesta es 503, nunca una respuesta solo con BM25.

Enfoque técnico: cuatro puertos de salida (embeddings, almacén vectorial, índice BM25, LLM generativo) detrás de un puerto de entrada `AskKnowledgeUseCase`. BM25 y RRF son Java puro en el dominio; los proveedores van en `infrastructure/adapter/out/`.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.9 / Angular 20 (frontend)

**Primary Dependencies**: Spring Boot 3.5.6; Spring AI 1.1.8 (BOM ya importado en `backend/pom.xml`); nuevo starter `spring-ai-starter-model-google-genai-embedding` (existe en 1.1.8, verificado en Maven Central); driver PostgreSQL existente; extensión pgvector (SQL nativo vía JDBC/JPA)

**Storage**: PostgreSQL 17 con extensión pgvector, misma base `skillbridge`. Schema dedicado `knowledge` con la tabla `knowledge_chunks` (migración `V14`); la extensión `vector` va en `public`. BM25 en memoria, reconstruido al indexar. Decisión de schema frente a base nueva: ver `research.md` R8.

**Testing**: JUnit 5 + Mockito (unitarias, sin red); MockMvc (controlador); Testcontainers con imagen pgvector (persistencia); doble determinista de embeddings y espía del LLM (aceptación); Karma/Jasmine ChromeHeadless (frontend).

**Target Platform**: Linux server (Docker Compose local; Railway en producción)

**Project Type**: web application (backend Spring Boot + frontend Angular)

**Performance Goals**: sin objetivo de 50 ms (SC-002 revisado). Se miden y reportan la latencia de embeddings y la del LLM por separado. El índice BM25 responde en < 5 ms con el corpus actual (`[INFERRED]`, supuesto de la spec).

**Constraints**: solo contenido público indexado (FR-004); sin datos de usuario al proveedor de embeddings; `GEMINI_EMBEDDING_API_KEY` independiente de `GEMINI_API_KEY`; timeouts reales en cliente (FR-008); sin mocks en producción (FR-009).

**Scale/Scope**: < 100 fragmentos (ofertas activas, categorías, 3 documentos bilingües); ~30 preguntas de evaluación ES/EN.

## Constitution Check

*GATE: se evalúa antes de la investigación y de nuevo tras el diseño.*

| Principio | Estado | Cómo se cumple |
|---|---|---|
| I.1 Dominio puro | PASS | `KnowledgeQuery`, `KnowledgeChunk`, `KnowledgeAnswer`, BM25, RRF y coseno sin imports de Spring ni JPA. |
| I.2 Puertos | PASS | Entrada: `AskKnowledgeUseCase`. Salida: `KnowledgeSearchPort`, `KnowledgeIndexPort`, `EmbeddingPort`, `KnowledgeAnswerPort`. El controlador nunca toca repositorios. |
| I.3 Adaptadores | PASS | REST en `adapter/in/rest/`; pgvector en `adapter/out/persistence/knowledge/` con entidad JPA y conversión a dominio; Gemini en `adapter/out/ai/`. |
| II.1 Puertos de IA | PASS con nota | `EmbeddingPort` es nuevo. La constitución lista los puertos de IA de forma no exhaustiva; se propone añadirlo en la enmienda que acompaña a esta feature. |
| II.2 Aislamiento del proveedor | PASS | Las SDK de Gemini solo aparecen en `infrastructure/adapter/out/ai/`. El dominio y la aplicación no conocen el proveedor. |
| III.1–2 Auth y CSRF | PASS | Endpoint protegido por `anyRequest().authenticated()` (spec, Assumptions); `POST` con cookie exige `X-XSRF-TOKEN`. |
| III.3 Zero-PAN | PASS | No aplica lógica de pagos. |
| IV Correlación | PASS | `X-Correlation-Id` en logs y en la respuesta; sin texto completo de la pregunta en logs (FR-010). |
| V.1 Verificación real | PASS (proceso) | Cada tarea reporta la ejecución real o `NOT RUN` con la causa. |
| V.3 Sin mocks en producción | PASS | Los dobles de embeddings y del LLM viven en `src/test/`. Sin fallback silencioso (FR-009, FR-017). |
| VI.2 Migraciones | PASS | Migración nueva `V14__knowledge_chunks.sql`. No se modifica ninguna existente (verificado en develop: V1–V13 presentes). |
| VI.3 Cambios | PASS | No se hace commit ni se cambia de rama desde el plan. |
| VIII.1 Documentación sincronizada | PENDING | Se añaden puertos y un endpoint nuevo, sin mensajería nueva. Actualizar `docs/MODULES/backend.md`, `docs/INTEGRATIONS.md` y `docs/API.md` en la tarea final. |
| VIII.2 ADR | PASS | ADR-0008 (Accepted). Si la verificación de R1 o R3 cambia una decisión, se añade un ADR de enmienda. |
| VIII.3 Clasificación SDD | PASS | Cambio grande y de riesgo medio-alto (nuevo proveedor, migración, endpoint público de IA). Requiere spec, plan y tasks completos. |
| IX Testing | PASS | Se respeta la matriz de `docs/TESTING.md`. |

**Resultado**: sin violaciones que requieran justificación en Complexity Tracking. Los PENDING se cierran en la tarea de documentación.

## Phase 0: Research

Documento: [research.md](research.md). Decisiones resueltas:

- **R1** Modelo `gemini-embedding-001`, 768 dimensiones (`output_dimensionality`). Motivo: 3072 supera el límite de indexación de pgvector `[INFERRED]`. Pendiente de verificar en el spike T0.
- **R2** Artefacto `spring-ai-starter-model-google-genai-embedding:1.1.8` **verificado en Maven Central**. Pendiente: que exponga la opción de dimensión.
- **R3** pgvector: imagen `pgvector/pgvector:pg17` para compose y tests; plantilla pgvector de Railway en producción. **Verificado** que la imagen existe; **pendiente** confirmar en el panel de Railway.
- **R4** Migración `V14` (no V9: `develop` ya tiene V1–V13). **Verificado**.
- **R5** Cuota aislada por cuenta. Pendiente de confirmar la unidad de cuota en la consola de Google.
- **R6** BM25 y RRF en Java puro, sin librería.
- **R7** Puntos de integración existentes verificados (cache de ofertas en `OfferingCommandService`; categorías sin gancho, a añadir).

## Phase 1: Design & Contracts

- **Modelo de datos**: [data-model.md](data-model.md). Tabla `knowledge_chunks` con `vector(768)` e índice HNSW coseno; índice BM25 en memoria con reemplazo atómico.
- **Contrato**: [contracts/knowledge-ask.md](contracts/knowledge-ask.md). `POST /api/ai/knowledge/ask`, errores 400/401/429/503 en ProblemDetail.
- **Validación**: [quickstart.md](quickstart.md). Es multi-módulo, afecta a una base con extensión nueva y a dos proveedores reales, así que se justifica.

### Estructura hexagonal (rutas reales del repositorio)

```text
backend/src/main/java/com/riwi/skillbridge/
├── domain/
│   ├── knowledge/                       # KnowledgeQuery, KnowledgeChunk, KnowledgeSource, KnowledgeAnswer (records)
│   └── service/knowledge/               # Bm25Scorer, ReciprocalRankFusion, CosineThreshold (puro Java)
├── application/
│   ├── port/in/AskKnowledgeUseCase.java
│   ├── port/out/
│   │   ├── KnowledgeSearchPort.java     # búsqueda híbrida: devuelve candidatos fusionados
│   │   ├── KnowledgeIndexPort.java      # upsert por contentHash, reemplazo atómico, lectura de hashes
│   │   ├── EmbeddingPort.java           # embed(texts) -> vectores; capacidad 'embedding' (ADR-0007)
│   │   └── KnowledgeAnswerPort.java     # LLM generativo, solo con contexto suficiente
│   └── service/knowledge/
│       ├── AskKnowledgeService.java     # orquesta: sanitizar, buscar, umbral, LLM, validar fuentes
│       ├── KnowledgeIndexService.java   # carga inicial por lotes; reindexa al invalidar caché
│       └── KnowledgeCorpusLoader.java   # parsea knowledge/*.md con visibility: public
└── infrastructure/
    ├── adapter/in/rest/
    │   └── KnowledgeController.java     # POST /api/ai/knowledge/ask
    ├── adapter/out/ai/
    │   ├── GeminiEmbeddingAdapter.java  # Spring AI, GEMINI_EMBEDDING_API_KEY, timeout
    │   └── GeminiKnowledgeAnswerAdapter.java
    ├── adapter/out/persistence/knowledge/
    │   ├── KnowledgeChunkEntity.java    # JPA + vector
    │   ├── JpaKnowledgeChunkRepository.java
    │   └── PgVectorKnowledgeAdapter.java  # implementa KnowledgeSearchPort y KnowledgeIndexPort
    └── config/
        └── KnowledgeProperties.java     # app.rag.* (topK, minScoreThreshold, timeout, maxContextChars, rrfK)

backend/src/main/resources/
├── db/migration/V14__knowledge_chunks.sql
├── application.yml                      # app.rag.* y spring.ai.google.genai.embedding
└── knowledge/*.md                       # ya existen (3 documentos bilingües)

backend/src/test/
├── java/.../domain/service/knowledge/   # BM25, RRF, coseno, umbral (sin red)
├── java/.../application/service/knowledge/  # AskKnowledgeService con dobles
├── java/.../infrastructure/adapter/in/rest/KnowledgeControllerTest.java   # MockMvc
├── java/.../infrastructure/adapter/out/persistence/knowledge/PgVectorKnowledgeAdapterTest.java  # Testcontainers pgvector
├── java/.../KnowledgeAskFlowIT.java     # aceptación: doble determinista de embeddings y espía de LLM
└── resources/knowledge-eval/            # ≥30 preguntas ES/EN + casos sin respuesta (FR-019)

frontend/src/app/features/ai.component.ts   # tarjetas de fuentes (User Story 3)
```

Nota de rutas: `ai.component.ts` está en `frontend/src/app/features/` según la spec. Se verifica al implementar la vista (T10).

### Puntos de integración de sincronización (FR-015, R7)

- Ofertas: `OfferingCommandService` ya invalida la caché en 4 operaciones. Se añade la reindexación de **solo** los fragmentos cambiados (FR-018). Se usa la misma invalidación; no se engancha a Kafka (decisión 2 de la spec).
- Categorías: `CategoryService` no invalida caché. Se añade la notificación desde `CategoryService` al `KnowledgeIndexService`.
- Arranque: carga inicial por lotes de `EmbeddingPort`.

### Flujo de consulta

1. Validación (400 si vacía o > 500).
2. Sanitización de delimitadores de la pregunta (FR-010). La pregunta no se registra completa.
3. `KnowledgeSearchPort`: BM25 sobre el índice en memoria y similitud coseno sobre pgvector (`topK` de cada lado), fusión RRF.
4. Umbral: si la similitud coseno máxima < `minScoreThreshold` → respuesta «sin información», sin LLM (FR-006).
5. Construcción del prompt con fragmentos recortados a `maxContextChars` (6.000) y con instrucciones de sistema inalterables.
6. `KnowledgeAnswerPort` con timeout real en cliente (FR-008). Timeout o indisponibilidad → 503.
7. Validación de fuentes: solo se devuelven los `sourceId` citados que existen en el contexto (FR-007, SC-003).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Dos mecanismos de recuperación (BM25 y coseno) más RRF | Decisión del propietario (2026-11-10): búsqueda exacta de nombres y línea base medible | Solo embeddings: sin línea base ni búsqueda exacta. Queda registrado en ADR-0008. |
| Cuarto puerto de salida (`EmbeddingPort`) | Capacidad `embedding` de la cadena de fallback (ADR-0007) | Llamar al starter desde el servicio de aplicación violaría la constitución Art. II.2. |

## Verificaciones abiertas

Se resuelven en el spike **T0** antes de codificar nada:

1. [UNKNOWN] Que `spring-ai-starter-model-google-genai-embedding:1.1.8` acepte la opción de dimensión para `gemini-embedding-001`. Método: leer el código del starter del jar descargado.
2. [UNKNOWN] Imagen pgvector concreta (`pgvector/pgvector:pg17` o con versión fijada) y que la plantilla de Railway con PostgreSQL 17 esté disponible. Método: panel de Railway.
3. [UNKNOWN] Unidad de cuota de Gemini (cuenta o proyecto). Método: consola de Google AI con las dos cuentas.
4. [INFERRED] Límite de 2000 dimensiones para índices HNSW en `vector`. Método: `CREATE INDEX` de prueba sobre `vector(768)` en el contenedor pgvector.
5. [UNKNOWN] Número de idiomas soportados por `gemini-embedding-001` (la fuente oficial no lo fija). La calidad ES/EN se mide con FR-019.

## Próximo paso

Tras aprobar este plan: `/speckit.tasks` genera `tasks.md`. Propuesta de orden (menor riesgo primero):

- **T0** Spike de verificaciones abiertas (1–5). Sin código de producción.
- **T1** Dominio puro: records y reglas del umbral.
- **T2** BM25 puro (con tests).
- **T3** RRF y coseno puros (con tests).
- **T4** Cargador de corpus desde `knowledge/*.md` (solo `visibility: public`).
- **T5** Migración `V14` y `PgVectorKnowledgeAdapter` con Testcontainers pgvector; cambio de imagen en compose y en los 3 tests.
- **T6** `GeminiEmbeddingAdapter` detrás de `EmbeddingPort`, con timeout y 503.
- **T7** `AskKnowledgeService` con dobles; rechazo temprano sin LLM.
- **T8** `GeminiKnowledgeAnswerAdapter` y validación de fuentes.
- **T9** `KnowledgeController` + MockMvc + `KnowledgeAskFlowIT`.
- **T10** Sincronización: caché de ofertas y gancho de categorías.
- **T11** Conjunto de evaluación y informe comparando BM25 solo, embeddings solos e híbrida.
- **T12** Frontend: tarjetas de fuentes (User Story 3).
- **T13** Documentación: `docs/MODULES/backend.md`, `docs/INTEGRATIONS.md`, `docs/API.md`; enmienda de constitución Art. II.1 si se aprueba.
