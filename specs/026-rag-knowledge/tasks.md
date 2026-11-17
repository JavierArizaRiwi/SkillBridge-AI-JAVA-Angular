# Tasks: HU-26 Asistente de Conocimiento con RAG (híbrido)

**Input**: `specs/026-rag-knowledge/plan.md` (Approved 2026-11-10), `spec.md` (Approved), `research.md` (R1–R8), `data-model.md`, `contracts/knowledge-ask.md`, `quickstart.md`

**Tests**: incluidos. La spec exige pruebas automatizadas (FR-013, FR-019, SC-007).

**Organización**: primero Setup (verificaciones y migración), luego Foundational (bloquea todas las historias), después una fase por historia en orden de prioridad (US1 P1, US2 P2, US3 P2, US4 P3) y al final Polish.

**Entrega a workers**: cada tarea se convierte en un archivo de spec para `scripts/agent-worktree-agy.sh` con un encabezado `## Acceptance criteria` y ≤48 KiB (SDD-WORKFLOW §2). Los criterios de cada tarea están debajo de ella.

**Marcas**:
- `[P]`: paralelizable (archivos distintos, sin dependencia pendiente).
- `[US#]`: tarea de historia de usuario.
- **(ACCIÓN DEL PROPIETARIO)**: requiere acceso a Railway, a la consola de Google o una decisión tuya. Un worker no puede hacerla.

## Format: `- [ ] [TaskID] [P?] [Story?] Descripción con ruta`

---

## Phase 1: Setup y verificaciones (T0)

**Propósito**: cerrar las verificaciones abiertas del plan antes de escribir código de producción.

- [x] T001 [P] Verificar en el jar de `spring-ai-starter-model-google-genai-embedding:1.1.8` si existe la opción de dimensión (`output_dimensionality` o `dimensions`) para `gemini-embedding-001`. Actualizar R1 y R2 de `specs/026-rag-knowledge/research.md` con el resultado.
  - Criterios: el resultado queda escrito en R1/R2 como `[VERIFIED]` con la clase o propiedad encontrada, o como `[UNKNOWN]` con el motivo. No se modifica código.

- [ ] T002 [P] (ACCIÓN DEL PROPIETARIO) Confirmar en el panel de Railway que hay una plantilla pgvector con PostgreSQL 17 disponible, y ejecutar `CREATE EXTENSION IF NOT EXISTS vector;` en la base de producción. Anotar el resultado en R3 de `research.md`.
  - Criterios: R3 registra el nombre exacto de la plantilla, la versión de PostgreSQL y la salida de `\dx vector`.

- [ ] T003 [P] (ACCIÓN DEL PROPIETARIO) En la consola de Google AI con las dos cuentas, confirmar si la cuota de `gemini-embedding-001` se cuenta por cuenta o por proyecto. Anotar el resultado en R5 de `research.md`.
  - Criterios: R5 indica la unidad de cuota confirmada y la fecha de la comprobación.

- [x] T004 Spike local en Docker: arrancar `pgvector/pgvector:pg17`, crear el schema `knowledge` y una tabla de prueba con `vector(768)` e índice HNSW `vector_cosine_ops`. Documentar el resultado en R8 y en el supuesto de límite de dimensiones de `plan.md`.
  - Criterios: `CREATE INDEX` con HNSW sobre `vector(768)` termina sin error (salida observada). Si Docker no está disponible, se reporta `NOT RUN — Not executed in this session` con la causa.

- [x] T005 Crear la migración `backend/src/main/resources/db/migration/V14__knowledge_chunks.sql` con el SQL de `data-model.md` (schema `knowledge`, tabla `knowledge_chunks`, índice HNSW). Depende de T004.
  - Criterios: el archivo coincide con el SQL de `data-model.md`. No se modifica ninguna migración V1–V13 (constitución Art. VI.2). `mvn -f backend/pom.xml -o validate` pasa.

- [x] T006 Cambiar la imagen de PostgreSQL a `pgvector/pgvector:pg17` en `docker-compose.yml` (servicio `postgres`) y en `BookingCancellationIT.java`, `BookingPersistenceAdapterTest.java` y `JpaOfferingRepositoryTest.java`. Depende de T004.
  - Criterios: las 4 referencias usan la misma imagen. `grep -rn "postgres:17-alpine" docker-compose.yml backend/src/test` no devuelve resultados. Las pruebas que no requieren Docker siguen compilando (`mvn -o validate`).

---

## Phase 2: Foundational (bloquea todas las historias)

**Propósito**: dominio puro, puertos y adaptadores base. Ninguna historia puede empezar antes de terminar esta fase.

- [x] T007 Crear los modelos de dominio puros en `backend/src/main/java/com/riwi/skillbridge/domain/knowledge/`: `KnowledgeQuery`, `KnowledgeChunk` (con `SourceType` `OFFERING|CATEGORY|POLICY_DOC` y `Language` `ES|EN|MIXED`), `KnowledgeSource`, `KnowledgeAnswer`. Son records o enums sin imports de Spring ni JPA.
  - Criterios: cada campo del `KnowledgeChunk` sigue `data-model.md`: `sourceId` identificador único (`offering:<id>`, `category:<id>` o el `sourceId` del `.md`); `title` no vacío. `KnowledgeQuery.question` cumple FR-002 textual: «longitud mínima de 1 carácter no blanco y longitud máxima de 500 caracteres».
  - Criterios: prueba unitaria `KnowledgeQueryTest` cubre vacío, solo espacios, 1 carácter, 500 y 501 caracteres.
  - Criterios: `grep -rn "org.springframework\|jakarta" domain/knowledge` no devuelve resultados.

- [x] T008 [P] Implementar la similitud coseno pura y la regla de umbral en `backend/src/main/java/com/riwi/skillbridge/domain/service/knowledge/CosineSimilarity.java` y `.../ThresholdRule.java`. La regla: «rechazo si la similitud coseno máxima es menor que `minScoreThreshold`» (FR-006).
  - Criterios: `CosineSimilarityTest` verifica vectores iguales (1.0), ortogonales (0.0), opuestos (-1.0) y vector nulo (error controlado). `ThresholdRuleTest` verifica que la regla usa solo la similitud coseno, nunca la puntuación fusionada.

- [x] T009 [P] Implementar BM25 puro en `backend/src/main/java/com/riwi/skillbridge/domain/service/knowledge/Bm25Scorer.java` con `k1 = 1.2` y `b = 0.75` (R6). Sin dependencias externas.
  - Criterios: `Bm25ScorerTest` verifica que un término raro puntúa más que uno común, que una palabra repetida tiene rendimientos decrecientes, y que un documento largo se penaliza con `b > 0`. Los valores esperados se calculan a mano en el test.

- [x] T010 [P] Implementar Reciprocal Rank Fusion puro en `backend/src/main/java/com/riwi/skillbridge/domain/service/knowledge/ReciprocalRankFusion.java`, con `score(d) = Σ 1 / (k + rango_r(d))` y `k = 60` por defecto (R6, ADR-0008).
  - Criterios: `ReciprocalRankFusionTest` verifica que un documento que aparece en ambos rankings supera a uno que aparece en uno solo, y que `k` es configurable. El cálculo esperado está escrito a mano en el test.

- [x] T011 Crear `KnowledgeCorpusLoader` en `backend/src/main/java/com/riwi/skillbridge/application/service/knowledge/`. Lee `backend/src/main/resources/knowledge/*.md`, parsea el encabezado (`sourceId`, `type`, `visibility`) e indexa solo los archivos con `visibility: public`, por sección de idioma (FR-004, FR-014). Depende de T007.
  - Criterios: `KnowledgeCorpusLoaderTest` verifica que un archivo sin `visibility: public` se descarta. Verifica que los 3 documentos reales (`cancellation-policy.md`, `mentorships-how-it-works.md`, `platform-faq.md`) producen fragmentos con `sourceId` no vacío y `language` ES o EN.
  - Criterios: el loader no lee `docs/` ni ningún otro directorio (FR-004, exclusión de documentación interna).

- [x] T012 Crear los puertos: `backend/src/main/java/com/riwi/skillbridge/application/port/in/AskKnowledgeUseCase.java` (entrada) y en `application/port/out/`: `KnowledgeSearchPort`, `KnowledgeIndexPort`, `EmbeddingPort`, `KnowledgeAnswerPort`. Depende de T007.
  - Criterios: las interfaces usan solo tipos de dominio. `KnowledgeSearchPort` devuelve candidatos fusionados con su similitud coseno. `EmbeddingPort` expone `embed(List<String>)`. `KnowledgeAnswerPort` recibe contexto ya validado.
  - Criterios: `grep -rn "org.springframework" application/port` no devuelve resultados (constitución Art. I.2).

- [x] T013 [P] Crear `KnowledgeProperties` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/` con prefijo `app.rag` (`topK=3`, `minScoreThreshold`, `timeoutSeconds`, `maxContextChars=6000`, `rrfK=60`). Añadir los valores en `backend/src/main/resources/application.yml`.
  - Criterios: `KnowledgePropertiesTest` verifica los valores por defecto. `minScoreThreshold` no tiene valor por defecto inventado: se deja explícito como pendiente de calibración (FR-019) y el arranque lo exige.

- [x] T014 Implementar `GeminiEmbeddingAdapter` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/`, detrás de `EmbeddingPort`. Usa `spring-ai-starter-model-google-genai-embedding` con `GEMINI_EMBEDDING_API_KEY` (no `GEMINI_API_KEY`), `gemini-embedding-001` y 768 dimensiones. Depende de T001, T012 y T013.
  - Criterios: sin `GEMINI_EMBEDDING_API_KEY`, el adaptador no arranca y el puerto lanza una excepción mapeada a 503 (FR-017). Nunca reutiliza la clave del LLM.
  - Criterios: el timeout se aplica en el cliente (FR-008). `GeminiEmbeddingAdapterTest` con un doble de la API verifica el timeout y el mapeo de error a `AiProviderException`.
  - Criterios: `docker-compose.yml` y `application.yml` referencian `GEMINI_EMBEDDING_API_KEY`; no se escribe ninguna clave real en el repositorio (AGENTS.md §3).

- [x] T015 Implementar la persistencia pgvector: `KnowledgeChunkEntity` (JPA, `@Table(schema = "knowledge")`), `JpaKnowledgeChunkRepository` y `PgVectorKnowledgeAdapter` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/knowledge/`. Implementa `KnowledgeSearchPort` (búsqueda por similitud coseno) y `KnowledgeIndexPort` (upsert por `contentHash`, reemplazo atómico). Depende de T005, T007 y T012.
  - Criterios: la entidad y las consultas nativas usan el nombre calificado `knowledge.knowledge_chunks`. La conversión entidad↔dominio está en el adaptador (constitución Art. I.3). `vector(768)` coincide con la dimensión de `EmbeddingPort`.
  - Criterios: el reemplazo del índice es transaccional: una lectura concurrente nunca ve un índice parcial (FR-015).

- [x] T016 Prueba de persistencia `PgVectorKnowledgeAdapterTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/knowledge/` con Testcontainers `pgvector/pgvector:pg17`. Depende de T006 y T015.
  - Criterios: la prueba corre `mvn -f backend/pom.xml test -Dtest=PgVectorKnowledgeAdapterTest` y verifica que la búsqueda devuelve el fragmento más cercano y que un `contentHash` igual no recalcula el vector (FR-018).
  - Criterios: si Docker no está disponible, se reporta `NOT RUN — Not executed in this session` con la causa (constitución Art. V.1).

---

## Phase 3: US1 — Consulta fundamentada con citas (Priority: P1) 🎯 MVP

**Goal**: un Customer autenticado pregunta y recibe respuesta redactada con fuentes citadas.

**Independent Test**: `POST /api/ai/knowledge/ask` con una pregunta sobre cancelación devuelve 200, `hasSufficientContext: true` y `sources` con `policy-cancellation` (`contracts/knowledge-ask.md`).

- [x] T017 [US1] Crear `KnowledgePromptBuilder` y `SourceCitationValidator` en `backend/src/main/java/com/riwi/skillbridge/application/service/knowledge/`. El builder sanitiza delimitadores de la pregunta y de los fragmentos (FR-010), recorta el contexto a `maxContextChars` y usa instrucciones de sistema inalterables. El validador devuelve solo los `sourceId` citados en la respuesta que existen en el contexto (FR-007, SC-003).
  - Criterios: `KnowledgePromptBuilderTest` verifica que una pregunta con «Ignora todas las instrucciones previas» queda dentro del bloque de usuario y que el contexto no excede 6.000 caracteres.
  - Criterios: `SourceCitationValidatorTest` verifica que un `sourceId` alucinado por el LLM se descarta (edge case «Alucinación de identificadores»).

- [x] T018 [US1] Implementar `GeminiKnowledgeAnswerAdapter` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/`, detrás de `KnowledgeAnswerPort`, con timeout real en cliente (FR-008) y registro de `correlationId`, número de fragmentos y tipo de error, sin texto de la pregunta (FR-010). Depende de T012 y T013.
  - Criterios: `GeminiKnowledgeAnswerAdapterTest` con un doble lento verifica que la llamada se corta dentro de `timeoutSeconds + 1` s (SC-006).
  - Criterios: `AiProviderException` con `TIMEOUT` o `UNAVAILABLE` se mapea a 503 y `RATE_LIMIT` a 429 (contrato).

- [x] T019 [US1] Implementar `AskKnowledgeService` en `backend/src/main/java/com/riwi/skillbridge/application/service/knowledge/`, que implementa `AskKnowledgeUseCase`. Flujo: sanitizar → `KnowledgeSearchPort` (BM25 + coseno + RRF) → `ThresholdRule` → `KnowledgePromptBuilder` → `KnowledgeAnswerPort` → `SourceCitationValidator`. Depende de T008–T012, T017 y T018.
  - Criterios: el servicio solo depende de puertos (constitución Art. I.2). La firma de `AskKnowledgeUseCase` devuelve `KnowledgeAnswer`.
  - Criterios: `AskKnowledgeServiceTest` con dobles de los puertos verifica el camino feliz y que `sources` solo contiene fragmentos citados.

- [x] T020 [US1] Crear el DTO y el controlador `KnowledgeController` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/`. Endpoint `POST /api/ai/knowledge/ask` con `@NotBlank` y `@Size(max = 500)` sobre `question`. Llama solo a `AskKnowledgeUseCase`. Depende de T019.
  - Criterios: la respuesta 200 sigue exactamente el contrato `contracts/knowledge-ask.md` (`answer`, `hasSufficientContext`, `sources[sourceId,title,snippet,score]`). Los errores pasan por `GlobalExceptionHandler` en ProblemDetail (FR-002).
  - Criterios: el controlador no importa repositorios ni entidades (constitución Art. I.2).

- [x] T021 [US1] Prueba de controlador `KnowledgeControllerTest` (MockMvc) en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/`. Depende de T020.
  - Criterios: verifica 200 con la forma del contrato, 400 para `"   "` y para 501 caracteres, y 401 sin autenticación (FR-001, SC-001).
  - Criterios: `mvn -f backend/pom.xml test -Dtest=KnowledgeControllerTest` ejecutado y su salida observada en la sesión.

- [x] T022 [US1] Implementar `KnowledgeIndexService` en `backend/src/main/java/com/riwi/skillbridge/application/service/knowledge/`. Carga inicial al arrancar por lotes: corpus de `.md` (T011), ofertas activas (`OfferingRepositoryPort.findAllActive()`) y categorías activas (`CategoryRepositoryPort.findAllActive()`). Depende de T011, T014, T015 y T019.
  - Criterios: la carga inicial usa `EmbeddingPort` por lotes y reemplaza el índice de forma atómica (FR-015, FR-018). El arranque registra el número de fragmentos indexados sin imprimir contenido.
  - Criterios: `KnowledgeIndexServiceTest` verifica que solo se recalculan los fragmentos con `contentHash` cambiado (FR-018).

- [x] T023 [US1] Sincronizar ofertas: `OfferingCommandService` (`backend/src/main/java/com/riwi/skillbridge/application/service/OfferingCommandService.java`) ya invalida la caché en cuatro operaciones. Añadir la reindexación de los fragmentos cambiados con `KnowledgeIndexService` en las mismas cuatro operaciones. No se engancha a Kafka (decisión 2 de la spec). Depende de T022.
  - Criterios: tras crear, actualizar, activar o desactivar una oferta, la siguiente consulta refleja el cambio (prueba de integración en T029).
  - Criterios: `OfferingCommandServiceTest` existente sigue en verde.

- [x] T024 [P] [US1] Sincronizar categorías: `CategoryService` (`backend/src/main/java/com/riwi/skillbridge/application/service/CategoryService.java`) no invalida caché. Añadir la notificación al `KnowledgeIndexService` en crear, actualizar y eliminar (FR-015). Depende de T022.
  - Criterios: `CategoryServiceTest` existente sigue en verde, y una prueba nueva verifica que un cambio de categoría reindexa solo ese fragmento.

- [x] T025 [US1] Prueba de aceptación `KnowledgeAskFlowIT` en `backend/src/test/java/com/riwi/skillbridge/`, con doble determinista de embeddings y espía del LLM generativo. Caso: pregunta sobre cancelación en ES y en EN (FR-016). Depende de T021, T022 y T023. — _Hecha sin BD:_ `KnowledgeAskFlowIT` usa servicio y corpus reales con dobles deterministas (MockMvc, 8 pruebas en verde con `mvn test -Dtest=KnowledgeAskFlowIT`). El camino con PostgreSQL real queda cubierto por `PgVectorKnowledgeAdapterTest` (7/7 con Docker) y por el arranque completo de `BookingCancellationIT`.
  - Criterios: `mvn -f backend/pom.xml verify -Dit.test=KnowledgeAskFlowIT` ejecutado con salida observada. Sin red ni claves reales (SC-007).

---

## Phase 4: US2 — Rechazo temprano y protección de costos (Priority: P2)

**Goal**: preguntas fuera de dominio o con similitud baja responden «sin información» sin llamar al LLM.

**Independent Test**: pregunta «¿Cómo reparar la transmisión de un diésel?» → 200, `hasSufficientContext: false`, `sources: []`, cero llamadas al LLM (espía).

- [x] T026 [US2] Prueba unitaria `KnowledgeEarlyRejectionTest` en `backend/src/test/java/com/riwi/skillbridge/application/service/knowledge/`. Con un espía de `KnowledgeAnswerPort`, verifica que por debajo del umbral no hay llamadas al LLM y que la respuesta es determinista (FR-006, SC-002). — `KnowledgeEarlyRejectionTest` 3/3.
  - Criterios: el mensaje de «sin información» es constante y no inventa datos (SC-005). El test corre sin red.

- [x] T027 [US2] Pruebas de casos límite en `KnowledgeAskFlowIT`: pregunta de datos privados («reservas de otros clientes», «correos de mentores») devuelve `hasSufficientContext: false`; prompt injection no revela directivas internas; pregunta de 501 caracteres devuelve 400. Depende de T025. — casos en `KnowledgeAskFlowIT` (datos privados, inyección, 501 caracteres).
  - Criterios: ninguna respuesta incluye correos, contraseñas ni datos de pago (SC-004).

- [x] T028 [US2] Escribir el conjunto de evaluación `backend/src/test/resources/knowledge-eval/questions.json` con al menos 30 preguntas en ES y EN, con fragmentos esperados y casos sin respuesta. Esta tarea es un borrador que revisa el propietario antes de usarse: define qué cuenta como acierto. Depende de T005 y del corpus existente.
  - Criterios: al menos 30 preguntas, de las cuales al menos 6 son sin respuesta. Cada pregunta indica `expectedSourceIds` o `answerable: false`. Incluye pares ES↔EN sobre la misma política (FR-019).
  - Criterios: el archivo indica que es un borrador pendiente de revisión por el propietario (ACCIÓN DEL PROPIETARIO: revisar y aprobar).

- [x] T029 [US2] Implementar el informe de evaluación `KnowledgeEvaluationTest` en `backend/src/test/java/com/riwi/skillbridge/application/service/knowledge/`. Calcula `hit@k`, `MRR` y falsos positivos para tres estrategias: BM25 solo, embeddings solos e híbrida. Depende de T028 y T019. — `KnowledgeEvaluationTest` genera `evaluation-report.md`; cifras con dobles deterministas, no con Gemini.
  - Criterios: el informe se escribe en `specs/026-rag-knowledge/evaluation-report.md` con las tres filas y las métricas. Las cifras salen de una ejecución real (salida observada), no de estimaciones.

- [ ] T030 [US2] (ACCIÓN DEL PROPIETARIO) Revisar el informe de T029 y decidir `minScoreThreshold` y `rrfK`. Actualizar `application.yml` y ADR-0008 (sección «Fusión híbrida») con los valores elegidos y la justificación. Depende de T029.
  - Criterios: el umbral aparece en `application.yml` con un comentario que apunta a `evaluation-report.md`. Si la híbrida no supera a embeddings solos, se documenta la decisión de simplificar y no se oculta el resultado.

---

## Phase 5: US3 — Respuestas y fuentes en el Chat IA (Priority: P2)

**Goal**: el chat muestra la respuesta, el estado de contexto y tarjetas de fuentes.

**Independent Test**: en el chat, una pregunta sobre mentorías muestra texto, tarjetas con título, extracto y porcentaje de relevancia.

- [x] T031 [P] [US3] Crear el cliente HTTP en `frontend/src/app/core/knowledge.api.ts` que llama a `POST /api/ai/knowledge/ask` con `apiBase()` (`frontend/src/app/core/api.ts`) y envía `X-XSRF-TOKEN` (constitución Art. III.2).
  - Criterios: tipos `KnowledgeAnswer` y `KnowledgeSource` coinciden con `contracts/knowledge-ask.md`. No se guarda el token en `localStorage`.

- [x] T032 [US3] Actualizar `frontend/src/app/features/ai.component.ts` con un modo de pregunta de conocimiento: respuesta reactiva con Signals, aviso neutro cuando `hasSufficientContext: false` (sin sección de fuentes), y tarjetas de fuentes con título, extracto y relevancia en porcentaje (User Story 3, escenarios 1 y 2). Depende de T031.
  - Criterios: `ng build` pasa. Las clases usan Tailwind v4 y Signals (AGENTS.md §1).

- [x] T033 [US3] Manejo de error en el mismo componente: con 503 o timeout muestra mensaje amigable con opción de reintentar, y conserva el texto del campo de entrada (User Story 3, escenario 3). Depende de T032.
  - Criterios: prueba Karma en `frontend/src/app/features/ai.component.spec.ts` cubre los tres escenarios. Se ejecuta con `npm test -- --watch=false --browsers=ChromeHeadless` y se reporta la salida observada.

---

## Phase 6: US4 — Resiliencia, timeouts y sin mocks silenciosos (Priority: P3)

**Goal**: los fallos de proveedor terminan en 503 controlado, nunca en datos inventados.

**Independent Test**: sin `GEMINI_EMBEDDING_API_KEY` o con timeout, la respuesta es 503 ProblemDetail.

- [x] T034 [US4] Prueba de timeout en `KnowledgeAskFlowIT`: un LLM que tarda más de `timeoutSeconds` provoca 503 en un plazo no mayor a `timeoutSeconds + 1` s (SC-006, FR-008). Depende de T025 y T018. — en `KnowledgeAskFlowIT` (503 medido en ~228 ms con LLM falso).
  - Criterios: el tiempo medido aparece en la salida de la prueba.

- [x] T035 [US4] Prueba de fallo de embeddings: sin `GEMINI_EMBEDDING_API_KEY`, o con el doble de embeddings caído, la pregunta devuelve 503 ProblemDetail y nunca una respuesta solo con BM25 (FR-017, SC-005). Depende de T014 y T025. — en `KnowledgeAskFlowIT` (embeddings caídos → 503, el LLM no se invoca).
  - Criterios: el test verifica que el mensaje no contiene datos fijos ni precios.

- [x] T036 [US4] Prueba de no-mocks en producción: el perfil por defecto no activa ningún adaptador mock de conocimiento. `app.ai.provider=mock` solo se activa de forma explícita (FR-009, constitución Art. V.3). Depende de T014 y T018.
  - Criterios: `grep -rn "Mock\|mock" backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai` no devuelve implementaciones que devuelvan datos fijos.

- [x] T037 [US4] Prueba de higiene de logs: con una pregunta con texto sensible, los logs no contienen el texto completo de la pregunta ni claves (FR-010). Depende de T018.
  - Criterios: el test captura los logs del `AskKnowledgeService` y verifica `correlationId` presente, texto de pregunta ausente.

---

## Phase 7: Preselección RAG para recomendaciones (FR-020)

**Goal**: el LLM de recomendaciones no lee todo el catálogo; recibe solo las ofertas más relevantes.

- [x] T042 `KnowledgeSearchPort.searchByType(query, topK, SourceType)` y su implementación en `PgVectorKnowledgeAdapter` (filtra por tipo antes de rankear). Prueba `PgVectorKnowledgeAdapterTest` (pgvector real).
- [x] T043 `AiRecommendationService`: preselección con `app.rag.recommendation-shortlist-size` (15), con fallback al catálogo completo. `AiRecommendationShortlistTest` 7/7.
- [x] T044 Configuración: `application.yml`, `docker-compose.yml`, `.env.example` (`RAG_RECOMMENDATION_SHORTLIST_SIZE`).
- [x] T045 Verificación real con Gemini: con tamaño 2 sobre 3 ofertas, el log muestra «Preselección RAG: 2 de 3» y la recomendación de «Java con Spring Boot» devuelve la oferta Java (200).
- [x] T046 Texto indexado de ofertas enriquecido (`KnowledgeIndexService.offeringContent`: título, categoría, modalidad, duración, precio y descripción). `KnowledgeIndexServiceTest` actualizada (15/15).
- [x] T047 Catálogo de prueba `scripts/dev/seed-rag-offerings.sql` (43 ofertas en 12 temas, idempotente) y objetivos `specs/026-rag-knowledge/recommendation-eval.json` (20).
- [x] T048 Comparación real con Gemini (46 ofertas activas, 20 objetivos): catálogo completo 20/20 top-1; preselección N=10 20/20; N=5 19/20 (falló «monolito → microservicios»). Ver `progress.md`.
- [x] T049 Arranque diferencial: `KnowledgeIndexService.loadInitialIndex` reutiliza los vectores con `contentHash` vigente, calcula solo lo nuevo o cambiado en lotes de 50, elimina lo sobrante y aplica todo con `KnowledgeIndexPort.applyChanges` en una transacción (FR-015, FR-018). Pruebas: `KnowledgeIndexServiceTest` y `PgVectorKnowledgeAdapterTest`. Verificado en vivo: tras 3 reinicios 54 de 55 filas conservan su `updated_at`.
- [x] T050 Desactivar una oferta/categoría o eliminar una categoría la retira del índice (`KnowledgeIndexPort.deleteBySourceId`); antes quedaba hasta el siguiente reinicio. Verificado en vivo: 55 → 54 → 55 fragmentos al desactivar y reactivar.

---

## Final Phase: Polish y verificación cruzada

- [x] T038 Sincronizar documentación: `docs/MODULES/backend.md`, `docs/INTEGRATIONS.md`, `docs/API.md` y `docs/TESTING.md` (constitución Art. VIII.1). Depende de T025. — secciones HU-26 añadidas a backend.md, INTEGRATIONS.md, API.md y TESTING.md.
  - Criterios: los puertos nuevos, el endpoint y la matriz de pruebas aparecen en los documentos.

- [ ] T039 (ACCIÓN DEL PROPIETARIO) Decidir si se enmienda la constitución Art. II.1 para listar `EmbeddingPort` y el resto de puertos de conocimiento. Depende de T012.
  - Criterios: la decisión queda registrada como enmienda aprobada o rechazada en `.specify/memory/constitution.md`.

- [x] T040 Ejecutar la suite completa y reportar resultados observados: `mvn -f backend/pom.xml test`, `mvn -f backend/pom.xml verify` (requiere Docker), `npm run build` y `npm test -- --watch=false --browsers=ChromeHeadless` en `frontend/`. Depende de T033 y T038.
  - _Estado 2026-11-10:_ `mvn verify` BUILD SUCCESS (509 unitarias, 1 omitida; 20 de integración, 4 omitidas ya deshabilitadas). `npm run build` exit 0. `npm test` (Karma con Brave vía `CHROME_BIN`, exit 0): 34 de 34 en verde.
  - Criterios: cada comando reporta exit code y salida. Lo no ejecutado se marca `NOT RUN — Not executed in this session` con la causa (constitución Art. V.1).

- [ ] T041 Validar la guía `specs/026-rag-knowledge/quickstart.md` contra el sistema en marcha, incluido el paso de fallo de embeddings (paso 5). Depende de T040.
  - Criterios: cada caso de la tabla del paso 4 se reporta con el resultado observado.

---

## Dependencias

- **Setup** (T001–T006) antes de **Foundational** (T007–T016). T001, T002 y T003 son independientes entre sí; T002 y T003 dependen del propietario.
- **Foundational** bloquea todas las historias.
- **US1** (T017–T025) es el MVP. US2, US3 y US4 dependen de US1 en:
  - US2 depende de T019 (servicio) y T025 (flujo de integración).
  - US3 depende de T020 (contrato del endpoint).
  - US4 depende de T018 y T014.
- Dentro de cada fase, las tareas con `[P]` pueden ir en paralelo; el resto sigue el orden de numeración.

## Ejemplo de paralelización

- Foundational: T008, T009, T010 y T013 en paralelo (archivos distintos, sin dependencias).
- US1: T017 y T018 en paralelo; luego T019; luego T020 y T022; T023 y T024 en paralelo.
- US3: T031 en paralelo con las tareas de US1 que no tocan el frontend.

## Estrategia de implementación

1. **MVP**: Setup + Foundational + US1. Termina cuando el caso «cancelación» responde con cita y `KnowledgeAskFlowIT` pasa.
2. **Incremental**: US2 (rechazo y evaluación), luego US3 (UI), luego US4 (resiliencia).
3. **Revisión por tarea**: cada tarea se entrega como un run de `agent-worktree-agy.sh`, y el propietario revisa `changes.patch` antes de aplicarlo (SDD-WORKFLOW §3). Ninguna tarea se aplica al espacio de trabajo sin revisión.

## Resumen

- Total de tareas: 41.
- Por fase: Setup 6, Foundational 10, US1 9, US2 5, US3 3, US4 4, Polish 4.
- Acciones del propietario: T002 (Railway), T003 (consola de Google), T028 (revisar el borrador del conjunto de evaluación), T030 (decidir umbral y `rrfK`), T039 (enmienda de constitución).
- MVP sugerido: Setup + Foundational + US1 (T001–T025).
