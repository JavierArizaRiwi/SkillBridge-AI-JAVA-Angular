# HU-26 — Avance de implementación (asistente RAG)

> Documento de avance temporal. Se actualiza en cada pausa o cierre de carril. Fuente de tareas: [tasks.md](tasks.md).

Estado al 2026-11-11 (pausa solicitada). Rama: `feature/026-rag-knowledge`.

## Resumen

- **Terminadas: 36 de 41** (28 integradas + 7 de esta sesión, sin commit). Faltan T002, T003, T030, T039 (propietario) y T041 (ver «Cierre»).
- **Pausadas (sin cambios, se retoman):** L7 (T026, T029) y L8 (cableado de arranque, T025, T027, T034, T035). Sus worktrees siguen en disco pero sin ningún cambio ni reporte real; hay que relanzarlos.
- **Pendientes de agente:** T025, T026, T027, T029, T034, T035, T038, T040, T041.
- **Pendientes del propietario:** T002 y T003 (Railway y consola de Google), T030 (umbral `minScoreThreshold` y `rrfK` tras el informe real), T039 (enmienda de la constitución).

## Tareas integradas

| Commit | Tareas | Verificación |
|---|---|---|
| `c997a18` | SDD: spec, plan, tasks, data-model, contrato, quickstart | — |
| `93d7557` | T008, T009, T010 dominio puro: coseno, BM25, RRF | 29 pruebas, JDK 21 |
| `0473ff0` | T007 modelos de dominio | 52 pruebas (anidadas) |
| `72fdac3` | T013 propiedades `app.rag`, sin umbral inventado | 5 pruebas |
| `2b4ea4c` | T001 propiedades de Spring AI 1.1.8 verificadas en el jar | — |
| `eccea1a` | T005 migración `V14__knowledge_chunks.sql` | aplicada en pgvector/pg17 (T004) |
| `c416f31` | T006 imagen `pgvector/pgvector:pg17` | test-compile |
| `e0c086d`, `158783e` | T011 cargador del corpus, T012 puertos; corrección de segmentos `docs`/`knowledge` | 113 pruebas |
| `26eb54c` | T004 spike Docker: V14 aplicada, HNSW sobre `vector(768)` | prueba de humo |
| `8c147e3` | T011-R I/O de archivos fuera de `application` | 101 pruebas |
| `0de7a9e` | T015 adaptador pgvector | 130 pruebas |
| `a14a6fb` | T017 `KnowledgePromptBuilder`, `SourceCitationValidator` | 169 pruebas |
| `6ea943a` | T014 adaptador de embeddings Gemini; exclusión de autoconfigs sin clave | 13 pruebas + contexto sin clave |
| `d7045f6` | T016 prueba pgvector con Testcontainers; `ddl-auto: validate` | 5/5 con PostgreSQL 17 real |
| `0c6fbb3` | T018 adaptador de respuesta LLM | 10 pruebas |
| `bfffc5d` | T031 cliente HTTP del frontend | tsc app+spec; Karma 15/15 |
| `5b0aaa2` | T028 borrador de evaluación aprobado (42 preguntas, 12 sin respuesta) | JSON válido |
| `32ef99f` | T036 sin mocks silenciosos | 8 pruebas |
| `5898782` | T019 `AskKnowledgeService` | 16 pruebas |
| `cbfc1c4` | corrección de aislamiento: `app.ai.provider` se filtraba entre pruebas | suite IA+conocimiento 216/216 |
| `879e4ed` | documentación de avance y ejemplos de sourceId corregidos | — |
| `5953f78` | T032 chat del frontend con pestaña de conocimiento | tsc app+spec; Karma 9/9 |
| `55b25e3` | T037 higiene de logs | 4 pruebas |
| `91e6d3f` | T020, T021 controlador `POST /api/ai/knowledge/ask` | 9 pruebas MockMvc; 229 en el conjunto |
| `8dba831` | T022, T023, T024 índice y sincronización | 267 en el conjunto |

T033 queda cubierta por T032: sus pruebas de 503 y timeout con reintento y conservación del texto ya existen.

## Carriles pausados (retomar)

| Carril | Tareas | Estado | Acción al retomar |
|---|---|---|---|
| L7 | T026 → T029 | sin cambios; sin reporte real | relanzar con la misma spec |
| L8 | cableado de arranque → T025 → T027 → T034 → T035 | sin cambios; sin reporte real | relanzar con la misma spec |

**Resuelto (sin commit aún):** `KnowledgeConfiguration` registra los beans y un `ApplicationRunner` ejecuta `loadInitialIndex()` al arrancar (solo si `app.rag.min-score-threshold` está definido; sin él la funcionalidad queda apagada). KnowledgeControllerTest 9/9, CategoryServiceTest 17/17, OfferingCommandServiceTest 6/6.

**Hallazgo nuevo:** `PgVectorKnowledgeAdapter.search` solo hace búsqueda vectorial; BM25 + RRF existen en dominio pero no están conectados al camino de consulta. Hay que decidirlo antes de T029.

**Hallazgo anterior (ya resuelto arriba):** la carga inicial del índice existe (`KnowledgeIndexService.loadInitialIndex()`), pero no está conectada al arranque. Eso es el primer paso de L8. Mientras tanto el índice queda vacío al levantar el backend.

## Decisiones pendientes del propietario

1. **`snippet` de cada fuente** (T019): hoy es el contenido completo del fragmento. ¿Se recorta (p. ej. 200 caracteres) o se devuelve completo?
2. **Respuesta con contexto pero sin citas** (T019): si el LLM no cita ninguna fuente, la respuesta sale con `hasSufficientContext: true` y `sources` vacío. ¿Se marca como sin información?
3. **Umbral `minScoreThreshold` y `rrfK`** (T030): dependen del informe real con Gemini. El informe de T029 será con dobles deterministas y así debe leerse.
4. **Enmienda de la constitución** para listar `EmbeddingPort` y los puertos de conocimiento (T039).

## Problemas conocidos

- **Corregido en esta ronda:** `app.ai.provider` se filtraba entre pruebas (cbfc1c4). Los ids de ejemplo `cancellation-policy` se cambiaron por `policy-cancellation` (879e4ed).
- **Cliente del frontend (T031):** dos caminos (HttpClient y fetch) y un objeto que mezcla Observable con Promise. Funciona y está probado; es más de lo que pedía la spec.
- **Prueba de no-mocks (T036):** una aserción de mensaje es débil. No afecta el resultado.
- **Cambio grande en el chat (T032):** el diff es grande porque el contenido de recomendaciones se movió dentro de bloques `@if`. El modo por defecto sigue siendo recomendaciones.
- **Autenticación de GitHub:** la sesión `gh` caducó; hay que volver a iniciar sesión antes de hacer push.

## Verificación

Cifras de ejecuciones con JDK 21 (`/home/santiagojava/.jdks/azul-21.0.12.1`), en el worktree de integración o en el del worker antes de integrar. Las pruebas de Docker usan Testcontainers real (PostgreSQL 17 con pgvector 0.8.7). Lo que no se ha ejecutado se marca en la tarea correspondiente.


## Cierre de la sesión 2026-11-10 (sin commit)

- Híbrida conectada: `HybridRanker` (dominio) usado por `PgVectorKnowledgeAdapter`. Umbral provisional 0.5 en `application.yml`.
- **Bug corregido:** `source_id UNIQUE` chocaba con los fragmentos ES y EN de un mismo documento; ahora `UNIQUE (source_id, language)` en `V14` (editada in situ, rama sin desplegar) y las fuentes se deduplican por `sourceId`.
- Backend `mvn test`: 503 pruebas, 0 fallos, 2 errores solo por falta de Docker (`BookingPersistenceAdapterTest`, `PgVectorKnowledgeAdapterTest`). La migración `V14` editada **no se ha ejecutado** contra PostgreSQL en esta sesión.
- Frontend: `npm run build` OK (exit 0). `npm test`: NOT RUN (no hay Chrome ni Edge).
- T040: parcial. T041: NOT RUN (requiere sistema en marcha, Docker y claves Gemini).
- Snippet: se mantiene completo (decisión del propietario). Respuesta sin citas: sin cambios.

### Actualización con Docker disponible
- `mvn verify`: BUILD SUCCESS. Unitarias 509 (1 omitida), integración 20 (4 omitidas, ya deshabilitadas). Incluye `PgVectorKnowledgeAdapterTest` 7/7 contra `pgvector/pgvector:pg17` real (V14 con `UNIQUE (source_id, language)`, fusión híbrida, ES+EN del mismo `sourceId`).
- Brecha de integración corregida: `GeminiEmbeddingAdapter` tenía `@ConditionalOnBean(EmbeddingModel)` y, al estar excluida la autoconfiguración sin clave, nunca existía `EmbeddingPort`; con el RAG activo el contexto no arrancaba. Ahora el adaptador siempre existe, elige el modelo de Google entre varios `EmbeddingModel` y sin clave responde 503 al usarse (FR-017).
- `BookingCancellationIT` ahora excluye también las autoconfiguraciones de embeddings (su propiedad reemplaza la lista del yml).
- Karma ejecutado con Brave (`CHROME_BIN`): 34/34 en verde. T040 completa.
- Pendiente: T041 (requiere claves Gemini).

## Prueba con Gemini real (2026-11-10, pila completa en Docker)
- Arranque: la carga inicial indexó 12 fragmentos (3 políticas × ES/EN, 3 ofertas, 3 categorías) con embeddings reales de `gemini-embedding-001`.
- **Bug corregido:** la carga inicial fallaba con `InvalidDataAccessApiUsageException` porque el método por defecto `replaceIndex(chunks, embeddings)` se llamaba a sí mismo sin pasar por el proxy `@Transactional`. Ahora `PgVectorKnowledgeAdapter` lo sobrescribe con `@Transactional`.
- **Modelo del LLM:** `gemini-3.8-flash` no respondió en 40 s con la clave de pruebas; `gemini-3.1-flash-lite` responde en ~1,4 s. Se usó este último. Con 3.8 todo termina en 503 por timeout (10 s).
- Smoke test (modelo 3.1-flash-lite, umbral 0.5): cancelación ES/EN y FAQ responden con cita correcta; pregunta del diésel pasó el umbral (el LLM se negó solo, sin fuentes); `"   "` y 501 caracteres → 400.
- **Barrido de umbral (42 preguntas, 30 respondibles + 12 sin respuesta; una ejecución por umbral):**

| Umbral | Sin respuesta que citan fuente | Respondibles rechazadas | Fuente equivocada | Errores* |
|---|---|---|---|---|
| 0.50 | 5 | 0 | 0 | 6 |
| 0.60 | 5 | 7 | 2 | 3 |
| 0.65 | 2 | 9 | 1 | 1 |
| 0.70 | 0 | 11 | 1 | 2 |
| 0.75 | 0 | 22 | 0 | 4 |

\* Errores = peticiones sin 200 (varían entre ejecuciones; probable límite de cuota de Gemini, **no diagnosticado**). Las cifras son una sola ejecución cada una y no son definitivas.
- Lectura: con embeddings reales la separación es débil. Ningún umbral logra a la vez 0 falsos positivos y 0 rechazos indebidos; 0.65–0.70 es el mejor equilibrio observado. La decisión es de T030 (propietario).

## Preselección RAG de recomendaciones con catálogo grande (2026-11-11)
- Catálogo de prueba: 43 ofertas sembradas con `scripts/dev/seed-rag-offerings.sql` (+3 originales = 46 activas). El índice quedó en 55 fragmentos.
- Mejora de la indexación: cada oferta se indexa como título, categoría, modalidad, duración, precio y descripción (antes solo la descripción).
- Comparación con Gemini real (`gemini-3.1-flash-lite`), 20 objetivos con oferta esperada (`recommendation-eval.json`):

| Configuración | Top-1 correcto | Alguna correcta | Ofertas enviadas al LLM |
|---|---|---|---|
| Catálogo completo (N=0) | 20/20 | 20/20 | 46 |
| Preselección N=10 | 20/20 | 20/20 | 10 |
| Preselección N=5 | 19/20 | 19/20 | 5 |

- Con N=10 el LLM recibe ~78 % menos ofertas sin perder aciertos en esta muestra. Con N=5 se perdió «dividir un monolito en servicios» (la preselección no trajo la oferta de microservicios). Por eso el valor por defecto es 15.
- **Límites:** 20 objetivos escritos por mí conociendo el catálogo (sesgo del autor), una sola ejecución por configuración, y un solo LLM. No es una validación estadística.

## Título en recomendaciones (2026-11-11)
- El contrato de `Recommendation` añade `offeringTitle` (opcional). `AiRecommendationService` lo rellena desde el catálogo activo y descarta ids que la IA cite fuera del catálogo activo. La tarjeta del chat muestra el título. Pruebas: backend 8/8 en `AiRecommendationShortlistTest`, Karma 35/35.
- El log de preselección RAG lista los títulos elegidos.

## Reutilización de vectores y borrado del índice (2026-11-11)
- **Antes:** cada reinicio recalculaba todos los embeddings y desactivar una oferta o eliminar una categoría no la quitaba del índice hasta el siguiente reinicio.
- **Ahora:** `loadInitialIndex` compara el `contentHash` por (`sourceId`, idioma), embebe solo lo nuevo o cambiado (lotes de 50), borra lo sobrante y aplica todo en una transacción; si el proveedor de embeddings falla, el índice anterior queda intacto. Desactivar/eliminar llama a `deleteBySourceId`.
- **Evidencia:** `mvn verify` BUILD SUCCESS (535 unitarias, 1 omitida; 20 de integración, 4 omitidas ya deshabilitadas). En la pila real: reinicio sin cambios → 55 fragmentos, 54 con `updated_at` antiguo (no recalculados); desactivar una oferta por SQL y reiniciar → 54 (fragmento ausente); reactivar → 55 y solo esa fila con marca reciente.
- **Límite conocido:** `reindexChunk`/`reindexChunks` (cambios en caliente) siguen comparando solo por `contentHash`; dos fuentes con contenido idéntico (mismo título, categoría, modalidad, duración, precio y descripción) harían que la segunda no se indexe en caliente. En el arranque sí se distinguen por (`sourceId`, idioma).
- El log detallado del servicio (`System.Logger`) no sale en la consola de Docker; la evidencia anterior sale de la base de datos.
