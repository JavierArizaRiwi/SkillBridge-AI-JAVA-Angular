# HU-26 — Reporte de implementación del asistente RAG (para estudio)

> Qué se construyó, dónde está cada pieza, por qué se decidió así y con qué evidencia. Estado al 2026-11-11, rama `feature/026-rag-knowledge`.
> Documentos hermanos: `spec.md` (qué), `plan.md` (cómo), `tasks.md` (estado por tarea), `progress.md` (bitácora), `evaluation-report.md` (métricas), `docs/adr/0008-rag-retrieval-embeddings-and-vector-store.md` (decisión técnica).

## 1. Qué hace

Dos capacidades sobre el mismo índice vectorial:

1. **Asistente de conocimiento** (`POST /api/ai/knowledge/ask`): el usuario pregunta en español o inglés; el sistema recupera los fragmentos más relevantes (políticas, ofertas, categorías), el LLM redacta una respuesta **fundamentada** y devuelve las **fuentes citadas**. Si nada es suficientemente parecido, responde un mensaje fijo **sin llamar al LLM**.
2. **Preselección para recomendaciones** (FR-020): el LLM de recomendaciones ya no lee todo el catálogo; recibe solo las N ofertas más cercanas a la necesidad del usuario (N=15 por defecto).

## 2. Cómo funciona (flujo)

```
Pregunta ──► sanitizar ──► embedding de la pregunta (Gemini, 768 dim)
                              │
        Todos los fragmentos ─┤  ranking vectorial (coseno, pgvector)
        del índice            ├─ ranking léxico (BM25 en memoria)
                              ▼
                  Reciprocal Rank Fusion (k=60) ──► top-K (3)
                              │
           ¿máx. coseno ≥ umbral (0.5)? ── no ──► "sin información", 0 llamadas al LLM
                              │ sí
                      prompt con fragmentos + instrucciones fijas
                              ▼
                       LLM (Gemini) ──► respuesta con [sourceId]
                              ▼
          validar citas (solo ids que estaban en el contexto) ──► respuesta + fuentes
```

Puntos clave:
- El **umbral usa el coseno puro**, nunca la puntuación fusionada (RRF no es una similitud).
- El índice es chico, así que BM25 y la fusión se hacen en memoria sobre todos los fragmentos (`PgVectorKnowledgeAdapter.searchInternal`).
- **Recomendaciones:** `AiRecommendationService.shortlist` llama a `searchByType(..., OFFERING)`. Si el catálogo activo tiene ≤ N ofertas no se consulta el RAG. Si falla, usa el catálogo completo (es una optimización, nunca produce 503).

## 3. Dónde está cada pieza (backend, `backend/src/main/java/com/riwi/skillbridge/`)

| Capa | Archivo | Responsabilidad |
|---|---|---|
| Dominio (Java puro) | `domain/knowledge/` (`KnowledgeQuery`, `KnowledgeChunk`, `KnowledgeSource`, `KnowledgeAnswer`) | Modelos y reglas de validación (pregunta 1–500 caracteres) |
| | `domain/service/knowledge/` | `CosineSimilarity`, `Bm25Scorer` (k1=1.2, b=0.75), `ReciprocalRankFusion`, `ThresholdRule`, `HybridRanker` (combina los tres) |
| Puertos | `application/port/in/AskKnowledgeUseCase` | Entrada |
| | `application/port/out/` `KnowledgeSearchPort`, `KnowledgeIndexPort`, `EmbeddingPort`, `KnowledgeAnswerPort`, `KnowledgeCorpusSourcePort` | Salida (la capa de aplicación no conoce a Gemini ni a pgvector) |
| Aplicación | `application/service/knowledge/AskKnowledgeService` | Orquesta el flujo y aplica el umbral |
| | `KnowledgePromptBuilder`, `SourceCitationValidator` | Anti-inyección de prompt; descarta citas inventadas |
| | `KnowledgeIndexService`, `KnowledgeCorpusLoader` | Sincronización de arranque diferencial, reindexación por `contentHash` y borrado al desactivar; texto indexado de ofertas (`offeringContent`) |
| | `application/service/AiRecommendationService` | Preselección RAG + título en la recomendación |
| Infraestructura | `infrastructure/adapter/in/rest/KnowledgeController` | Endpoint REST |
| | `infrastructure/adapter/out/persistence/knowledge/` | `PgVectorKnowledgeAdapter`, entidad y repositorio JPA (schema `knowledge`) |
| | `infrastructure/adapter/out/ai/` | `GeminiEmbeddingAdapter` (clave propia `GEMINI_EMBEDDING_API_KEY`), `GeminiKnowledgeAnswerAdapter` (timeout en cliente) |
| | `infrastructure/adapter/out/knowledge/ClasspathKnowledgeCorpusSource` | Lee `resources/knowledge/*.md` |
| | `infrastructure/config/KnowledgeConfiguration` | Registra los beans y lanza la carga inicial; **solo se activa si existe `app.rag.min-score-threshold`** |
| | `infrastructure/config/KnowledgeProperties` | Propiedades `app.rag.*` |
| Datos | `resources/db/migration/V14__knowledge_chunks.sql` | Extensión `vector`, tabla, índice HNSW, `UNIQUE (source_id, language)` |
| | `resources/knowledge/*.md` | Corpus público: cancelación, mentorías, FAQ (cada uno con sección ES y EN) |

Frontend: `frontend/src/app/core/knowledge.api.ts` (cliente) y `features/ai.component.ts` (pestaña de conocimiento, tarjetas de fuente, errores con reintento, título en recomendaciones).

Configuración: `application.yml` (`app.rag.*`), `docker-compose.yml` (variables `RAG_*`), `.env.example`.

## 4. Decisiones y por qué

| Decisión | Motivo |
|---|---|
| pgvector en la misma PostgreSQL (imagen `pgvector/pgvector:pg17`) | Evita otro servicio; ADR-0008 |
| `gemini-embedding-001` a 768 dimensiones, clave propia | Cuota y fallos aislados del LLM; 768 cabe en el índice HNSW |
| Búsqueda híbrida BM25 + coseno + RRF | Los nombres propios y términos raros los captura el léxico; el significado, el embedding |
| Umbral sobre coseno puro, rechazo **antes** del LLM | Ahorra costo y evita respuestas inventadas (SC-002) |
| Sin mocks silenciosos: fallo de proveedor → 503 | Nunca devolver datos falsos (FR-009) |
| Corpus solo con `visibility: public` | No indexar información interna ni privada (FR-004) |
| Reindexar por `contentHash` al crear/editar ofertas y categorías | No recalcular embeddings sin cambios (FR-018) |

## 5. Problemas reales encontrados y corregidos

1. **`source_id UNIQUE` vs fragmentos ES/EN:** cada documento genera un fragmento por idioma con el mismo id; la carga inicial habría fallado. Ahora `UNIQUE (source_id, language)` (V14 editada in situ: si alguna base ya la aplicó, Flyway rechazará el checksum).
2. **Nunca había `EmbeddingPort` en la app real:** `GeminiEmbeddingAdapter` era `@ConditionalOnBean(EmbeddingModel)` y `application.yml` excluye ese modelo sin clave. Ahora el adaptador siempre existe y sin clave responde 503 al usarse.
3. **Varios `EmbeddingModel` (Google y OpenAI):** el adaptador elige explícitamente el de Google.
4. **Carga inicial sin transacción:** el método por defecto de la interfaz se llamaba a sí mismo y se saltaba el proxy `@Transactional` → `InvalidDataAccessApiUsageException`. Se sobrescribió en el adaptador.
5. **`BookingCancellationIT`** definía su propio `spring.autoconfigure.exclude`, lo que reactivaba la autoconfiguración de embeddings; se le añadieron las exclusiones.
6. **Modelo `gemini-3.8-flash`:** no respondió en 40 s con la clave de pruebas; se usa `gemini-3.1-flash-lite` (~1,4 s).
7. **Reinicio recalculaba todo y desactivar no borraba:** ahora el arranque es diferencial (reutiliza vectores por `contentHash`, lotes de 50, una transacción) y desactivar/eliminar retira el fragmento (`deleteBySourceId`).
8. **Título ausente en recomendaciones:** la API solo devolvía `offeringId`; ahora incluye `offeringTitle` y descarta ids fuera del catálogo activo.

## 6. Evidencia (qué se ejecutó de verdad)

| Prueba | Resultado |
|---|---|
| `mvn verify` (Docker + pgvector reales) | BUILD SUCCESS: 517+ unitarias (1 omitida; 535 en la última corrida) y 20 de integración (4 omitidas ya deshabilitadas) |
| `PgVectorKnowledgeAdapterTest` (pgvector real) | Orden por coseno, fusión híbrida, filtro por tipo, ES+EN del mismo `sourceId` |
| `KnowledgeAskFlowIT` / `KnowledgeEarlyRejectionTest` | Flujo HTTP con dobles deterministas: cita ES/EN, datos privados, inyección, 400, 401, 503 |
| Karma (Brave como Chrome headless) | 35/35 |
| Prueba real con Gemini | Cancelación ES/EN y FAQ responden con cita correcta; 400 en entradas inválidas |
| Arranque diferencial en la pila real | 3 reinicios: 54 de 55 filas sin recalcular; desactivar → 54, reactivar → 55 |
| Preselección de recomendaciones (46 ofertas, 20 objetivos) | Catálogo completo 20/20; N=10 20/20; N=5 19/20 |

Barrido de umbral con embeddings reales (una ejecución por umbral, sin descartar errores de cuota): 0.5 deja pasar 5 preguntas sin respuesta; 0.7 rechaza 11 respondibles. **No hay un umbral que separe limpiamente;** 0.65–0.70 es el mejor equilibrio observado.

## 7. Límites honestos

- El umbral **0.5 es provisional**; la calibración final (T030) es una decisión pendiente.
- `evaluation-report.md` se generó con un embedding falso de bolsa de palabras: valida el cableado, **no** la calidad con Gemini. En él la híbrida empata con BM25 solo.
- Los 20 objetivos de recomendaciones los escribí conociendo el catálogo (sesgo del autor) y se ejecutó una vez por configuración.
- Con 3 ofertas (catálogo original) la preselección nunca se activa; solo con más de 15 activas.
- Pendientes de otros: T002 (extensión `vector` en Railway), T003 (cuota por cuenta o proyecto), T030 (umbral y `rrfK`), T039 (enmienda de la constitución), T041 (recorrido completo de `quickstart.md`).

## 8. Cómo estudiarlo (orden sugerido)

1. `spec.md` (historias y requisitos FR-001…FR-020) → `docs/adr/0008-…` (por qué esta arquitectura).
2. `domain/service/knowledge/HybridRanker.java` y sus pruebas: es el núcleo y no depende de nada.
3. `AskKnowledgeService.java` → `KnowledgePromptBuilder` → `SourceCitationValidator`.
4. `PgVectorKnowledgeAdapter.java` (búsqueda híbrida) y `V14__knowledge_chunks.sql`.
5. `KnowledgeIndexService.java` (qué se indexa y cuándo) y `KnowledgeConfiguration.java` (cómo arranca).
6. `AiRecommendationService.shortlist` y `AiRecommendationShortlistTest`.
7. `KnowledgeAskFlowIT` para ver el flujo completo de punta a punta sin red.

Para verlo funcionando: `docker compose up --build -d`, y los comandos de la sección «Cómo ver el RAG» del chat (logs `Preselección RAG`, consultas SQL sobre `knowledge.knowledge_chunks`, la pestaña de conocimiento). Catálogo grande de prueba: `scripts/dev/seed-rag-offerings.sql`; objetivos: `recommendation-eval.json`.

## 9. Alcance de este PR

Solo contiene la HU-26. La paginación de ofertas y reservas se entrega en otro PR (`feature/pagination-offerings-bookings`), y la deuda de arquitectura heredada que se detectó al revisar la estructura está registrada en la issue #103. `frontend/public/env.js` puede aparecer modificado en el árbol de trabajo, pero solo por finales de línea que regenera `npm run build`.
