# Research: HU-26 Asistente RAG (híbrido)

Fecha: 2026-11-10. Solo se documentan las decisiones técnicas que bloquean la implementación. Los hechos ya establecidos en el repositorio no se repiten aquí.

## R1. Modelo de embeddings de Gemini y dimensión

- **Decision:** `gemini-embedding-001`, con dimensión de salida 768 configurada mediante `output_dimensionality` (propiedad `dimensions` en Spring AI).
- **Verified:** `[VERIFIED]` en esta sesión leyendo el bytecode del jar `~/.m2/repository/org/springframework/ai/spring-ai-google-genai-embedding/1.1.8/spring-ai-google-genai-embedding-1.1.8.jar` con `javap`. La clase `org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions` expone el campo `private Integer dimensions` con métodos `getDimensions()` / `setDimensions(Integer)` y builder `dimensions(Integer)`. Además, el enum `org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModelName` define explícitamente `GEMINI_EMBEDDING_001("gemini-embedding-001", "001", 3072, "Multilingual embedding model")`, y la clase `org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel` pasa dicho valor directamente a la API de Google GenAI mediante `EmbedContentConfig.builder().outputDimensionality(options.getDimensions())`.
- **Rationale:** el modelo por defecto devuelve 3072 dimensiones. Un `vector` de pgvector con índice HNSW admite como máximo 2000 dimensiones `[INFERRED]`, así que 3072 impide indexar. 768 cabe holgadamente y reduce almacenamiento. Fuente: documentación de Vertex AI sobre `output_dimensionality` y tamaño por defecto (<https://docs.cloud.google.com/vertex-ai/docs/generative-ai/model-reference/text>, <https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-text-embeddings?hl=en>).
- **Alternatives considered:**
  - `text-embedding-004` / `text-multilingual-embedding-002`: son los modelos que citan los ejemplos de Spring AI, pero son de generación anterior `[INFERRED]`. Se descartan por ser legados frente a `gemini-embedding-001`.
  - Dimensión 3072 sin índice: se descarta; búsqueda exacta funcionaría con 80 fragmentos, pero el índice es parte de lo que se quiere practicar.
- **Pendiente:** el número exacto de idiomas soportados no se confirmó en la documentación oficial (solo en una fuente de terceros). La calidad ES/EN se mide con FR-019, no se asume.

## R2. Artefacto de Spring AI para embeddings

- **Decision:** `org.springframework.ai:spring-ai-starter-model-google-genai-embedding`, versión gestionada por el BOM `spring-ai-bom` 1.1.8 ya importado en `backend/pom.xml`.
- **Verified:** `[VERIFIED]` en esta sesión inspeccionando los jars `~/.m2/repository/org/springframework/ai/spring-ai-google-genai-embedding/1.1.8/spring-ai-google-genai-embedding-1.1.8.jar` y `~/.m2/repository/org/springframework/ai/spring-ai-autoconfigure-model-google-genai/1.1.8/spring-ai-autoconfigure-model-google-genai-1.1.8.jar` (descargados con `mvn dependency:get`). Las clases de autoconfiguración y metadatos (`META-INF/spring-configuration-metadata.json`) confirman:
  - Clase de propiedades: `org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingProperties` (prefijo `spring.ai.google.genai.embedding.text`).
  - Propiedad exacta para dimensión: `spring.ai.google.genai.embedding.text.options.dimensions` (tipo `Integer`, mapeado a `outputDimensionality`).
  - Propiedad de modelo: `spring.ai.google.genai.embedding.text.options.model` (por defecto `gemini-embedding-001`).
  - Propiedad de API key dedicada para embeddings: `spring.ai.google.genai.embedding.api-key` (clase `GoogleGenAiEmbeddingConnectionProperties`), lo que permite aislar la cuota de embeddings respecto a la del chat sin colisión (R5).
- **Alternative rejected:** llamar a la API REST de Gemini con `RestClient`. Duplica lo que el starter ya resuelve y se aleja de la integración de Spring AI que ADR-0008 exige.

## R3. pgvector: imagen local, pruebas y Railway

- **Decision:**
  - `docker-compose.yml` y los 3 tests con Testcontainers (`BookingCancellationIT`, `BookingPersistenceAdapterTest`, `JpaOfferingRepositoryTest`) cambian `postgres:17-alpine` por una imagen con pgvector y PostgreSQL 17, p. ej. `pgvector/pgvector:pg17`. La versión concreta se fija en el spike T0.
  - Producción (Railway): usar la plantilla de pgvector de Railway (PostgreSQL 17), y ejecutar `CREATE EXTENSION IF NOT EXISTS vector;` en la base.
- **Verified:** la imagen `pgvector/pgvector` tiene tags para PostgreSQL 17 publicados en Docker Hub (<https://hub.docker.com/v2/repositories/pgvector/pgvector/tags/?name=pg17>). Railway indica que su imagen estándar de Postgres no incluye pgvector y que hay que usar una plantilla (<https://railway.com/guides/hosting-postgres-with-pgvector>). Lo confirmo en el panel de Railway antes de implementar, porque la verificación es de búsqueda y no del panel.
- **Impact:** cambiar la imagen en tests afecta a los 3 archivos de prueba. Por eso el cambio va en una tarea separada (T5), no mezclado con el adaptador.
- **Alternative rejected:** `VectorStore` en memoria de Spring AI en producción. No persiste vectores entre arranques (ADR-0008).

## R4. Numeración de la migración

- **Decision:** `backend/src/main/resources/db/migration/V14__knowledge_chunks.sql`.
- **Verified:** `develop` tiene migraciones de `V1` a `V13` (V10 zoom, V11 y V12 de HU-22 recommendation, V13 offering mentorship). La siguiente libre es **V14**. Esto corrige lo que dije antes: la última era V9 solo por un `ls | tail` ordenado de forma lexicográfica. Las migraciones existentes son inmutables (constitución, Art. VI.2).
- **Coordination:** si HU-22 o cualquier otra feature en curso reserva V14 antes de integrar, esta migración se renumera al siguiente libre. Verificar en el momento de implementar.

## R5. Cuota independiente de embeddings

- **Decision:** clave `GEMINI_EMBEDDING_API_KEY` de una cuenta de Google distinta a la del LLM (decisión del propietario, 2026-11-10). Sin respaldo a `GEMINI_API_KEY`.
- **Pendiente:** confirmar en la consola de Google AI (o Vertex) que la cuota de `gemini-embedding-001` se cuenta por cuenta o por proyecto. El aislamiento depende de eso. Es una verificación del spike T0 con la consola, no algo que pueda comprobar desde aquí.

## R6. BM25 y fusión RRF: implementación propia

- **Decision:** BM25 y RRF se implementan en Java puro dentro del dominio (sin dependencias externas de búsqueda).
- **Rationale:** son pocas líneas y son parte del aprendizaje que se quiere; la spec pide pruebas unitarias sin red (FR-013). Una librería de búsqueda añadiría una dependencia para algo que el corpus (<100 fragmentos) no necesita.
- **Parámetros:** `k1 = 1.2`, `b = 0.75` (valores estándar de BM25) y `k = 60` para RRF (valor habitual en la literatura). Se confirman con FR-019.

## R8. Schema dedicado `knowledge` en la misma base

- **Decision:** schema `knowledge` dentro de la base `skillbridge`; la extensión `vector` en `public`. Confirmado por el propietario (2026-11-10).
- **Rationale:** ADR-0006 pide una base por dueño, y el RAG pertenece al monolito, que ya es dueño de ofertas y categorías. Una base aparte obligaría a copiar esos datos y a sincronizarlos. Un schema aísla las tablas del RAG de las de reservas y pagos con poco costo.
- **Alternatives considered:**
  - Base de datos nueva: copia de datos y costo operativo sin beneficio para este alcance.
  - Tablas en `public` con prefijo `knowledge_`: menos aislamiento y sin ventaja real.
- **Riesgo a verificar en T0:** Flyway y el datasource actual (sin `currentSchema`) con una tabla calificada. Se prueba con Testcontainers en T5.

## R7. Puntos de integración existentes (verificados)

- `OfferingCachePort.evictActiveOfferings()` se llama desde `OfferingCommandService` en 4 operaciones (crear, actualizar, activar, desactivar); implementación Redis en `infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java`.
- `CategoryService` no invalida ninguna caché. FR-015 pide un gancho propio: se añade la notificación de cambio desde `CategoryService` al índice.
- `AiController` existe en `infrastructure/adapter/in/rest/`.
- Los puertos de IA actuales: `AiRecommendationPort`, `VisionPort`, `AiStructuredResponse`, en `application/port/out/`.

## Spike T004 (2026-11-10, Docker local)

- `[VERIFIED]` Imagen `pgvector/pgvector:pg17`: PostgreSQL 17.11 y extensión pgvector 0.8.7.
- `[VERIFIED]` `db/migration/V14__knowledge_chunks.sql` se aplica sin errores con `psql -v ON_ERROR_STOP=1`: extensión en `public`, schema `knowledge`, tabla `knowledge_chunks` e índice HNSW `knowledge_chunks_embedding_hnsw` sobre `vector(768)`.
- `[VERIFIED]` Prueba de humo: inserción de un vector de 768 dimensiones y búsqueda con `ORDER BY embedding <=> ...` devuelve la fila con similitud coseno 1.0000.
- `[UNKNOWN]` El plan de consulta (si el índice HNSW se usa) no se comprobó: con pocas filas el planificador usa búsqueda secuencial. Se verifica con el corpus real en T016.
- Sin resolver: el límite de 2000 dimensiones para HNSW no se probó por encima de 768; no hace falta, ya que la dimensión es 768.
