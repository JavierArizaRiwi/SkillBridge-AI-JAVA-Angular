# ADR-0008: Recuperación del RAG con embeddings y almacén vectorial

## Status

Accepted (aprobado por el propietario, 2026-11-10). Modifica la decisión de recuperación de la spec `specs/026-rag-knowledge` (HU-26, issue #77): sustituye «solo BM25 léxico en memoria, sin embeddings ni base vectorial» por una **recuperación híbrida**: BM25 y embeddings con almacén vectorial, fusionados con Reciprocal Rank Fusion. Decisión inicial del 2026-11-09: solo embeddings. **Revisada el 2026-11-10 por el propietario: híbrida desde el MVP.**

## Date

2026-11-09

## Context

La spec original de HU-26 fijó BM25 léxico en memoria y dejó los embeddings y las bases vectoriales «sujetos a justificación técnica formal aprobada». Esa elección salió de la tarea RAG-001 (octubre 2026), que midió un corpus pequeño y concluyó que BM25 bastaba. Hoy:

- El corpus sigue siendo pequeño: unas decenas de fragmentos (ofertas activas, categorías y 3 documentos de política bilingües) `[VERIFIED]` en `backend/src/main/resources/knowledge/`.
- El asistente debe responder en **español e inglés**. BM25 compara palabras, así que una pregunta en inglés no recupera ofertas escritas en español salvo por términos compartidos (limitación registrada como FR-016).
- El propietario del proyecto decidió **adoptar embeddings y un almacén vectorial**, con dos motivos: practicar estas técnicas y resolver la limitación entre idiomas.

**Honestidad sobre el motivo.** Con este volumen, BM25 sería suficiente para responder bien. Los embeddings se adoptan por aprendizaje y por el cruce de idiomas, no porque el rendimiento o la escala lo exijan. El ADR lo deja escrito para no justificar a posteriori algo que no es una necesidad técnica.

## Decision

La recuperación del MVP es **híbrida**, detrás del puerto `KnowledgeSearchPort`:
- **BM25** en memoria sobre los mismos fragmentos: encuentra nombres exactos, siglas y términos literales.
- **Embeddings + almacén vectorial** (similitud coseno): encuentra paráfrasis y cruza idiomas.
- **Fusión** de ambos rankings con *Reciprocal Rank Fusion* (ver «Fusión híbrida»).
BM25 **no es un respaldo**: si falla el servicio de embeddings, la respuesta es 503.

- La consulta y los fragmentos se convierten en vectores con un modelo de embeddings **multilingüe** y se buscan por similitud coseno.
- **Almacén vectorial:** pgvector en la PostgreSQL existente (recomendado; ver «Decisiones pendientes»), a través de `VectorStore` de Spring AI.
- **Proveedor de embeddings (decidido el 2026-11-09): Gemini**, a través de la integración de Spring AI, con **una clave propia** `GEMINI_EMBEDDING_API_KEY`, distinta de `GEMINI_API_KEY` (la del LLM generativo), para que el consumo de embeddings no sature la cuota del chat ni al revés; OpenAI (cuyo starter ya está en `pom.xml`) queda como segundo candidato de la cadena de fallback. **Se verifica en el plan** (no se asume): modelo exacto, dimensión del vector, artefacto de Spring AI para embeddings en la versión 1.1.8, soporte de español e inglés y cuota.
- **Embeddings como capacidad de IA:** pasa a ser una capacidad más de la cadena de fallback (`embedding`, ADR-0007) con su puerto de salida.
- **Rechazo temprano:** si ningún fragmento supera el umbral de similitud, se responde «sin información» **sin invocar al LLM generativo** (se mantiene FR-006). Calcular el embedding de la consulta no es una invocación al LLM generativo, pero sí una dependencia externa.
- **Sin respaldo léxico:** si el servicio de embeddings falla, la respuesta es **503**. Nunca respuestas simuladas.
- **Sincronización:** al invalidarse la caché de ofertas se recalculan **solo los embeddings de los fragmentos cambiados**; la carga inicial al arrancar es por lotes.
- **Medición obligatoria:** un conjunto de evaluación versionado de preguntas en español e inglés, con fragmentos esperados y casos sin respuesta, mide `hit@k`, `MRR` y falsos positivos. Sirve para calibrar el umbral y comparar modelos. Debe comparar **tres estrategias**: BM25 solo, embeddings solos e híbrida. Así se justifica con números que la fusión aporta algo; si no lo aporta, se documenta y se simplifica.

## Diferencias entre BM25 y embeddings

| Aspecto | BM25 (léxico) | Embeddings + almacén vectorial |
|---|---|---|
| Cómo puntúa | Cuenta coincidencias de palabras, pondera las raras y normaliza por longitud | Distancia entre vectores que representan el significado |
| Sinónimos y paráfrasis («tutoría» frente a «mentoría») | No los une | Los acerca |
| Entre idiomas | **No** | Sí, con un modelo multilingüe |
| Nombres exactos, siglas, identificadores | **Muy bien** | Puede diluirlos |
| Determinismo | Total | Depende del modelo; la misma pregunta puede cambiar si cambia el modelo |
| Explicabilidad | Alta: se ve qué palabras coincidieron | Baja: «estaba cerca en el espacio vectorial» |
| Infraestructura | Ninguna (memoria) | Extensión pgvector y migración, o un servicio vectorial |
| Dependencia externa en cada consulta | No | Sí (embedding de la pregunta), salvo modelo local |
| Coste y latencia | Despreciables | Llamada a un modelo por consulta y por fragmento cambiado |
| Pruebas | Sin red | Requiere un modelo de embeddings falso y determinista |
| Ajuste | Parámetros `k1` y `b`, umbral | Modelo, dimensión, métrica, umbral de similitud |
| Fallo típico | Falta de vocabulario común | Vecinos semánticos falsos; umbral mal calibrado; vectores obsoletos tras cambiar de modelo |
| Cuándo gana | Corpus pequeño, términos exactos, un solo idioma | Preguntas parafraseadas, varios idiomas, corpus grande |

La explicación didáctica, con ejemplos del proyecto, está en [`docs/guides/rag-bm25-vs-embeddings.md`](../guides/rag-bm25-vs-embeddings.md).

## Alternatives considered

- **Solo BM25 (decisión original).** Suficiente para el volumen, pero no cruza idiomas y no permite practicar embeddings y vector stores.
- **Solo embeddings (decisión inicial del 2026-11-09).** Más simple y con menos código, pero sin búsqueda exacta de nombres ni línea base léxica para medir. **Sustituida** el 2026-11-10 por la híbrida, a petición del propietario.
- **Híbrida (adoptada).** Da búsqueda exacta de nombres y línea base determinista, y permite comparar las tres estrategias. El coste es más código y más parámetros (`k` de RRF, umbral).
- **Base vectorial dedicada (Qdrant, Milvus…).** Añade un servicio que desplegar y alojar. Se descarta por ahora; el puerto permite añadirla después.
- **`VectorStore` en memoria de Spring AI.** Practica embeddings sin base de datos, pero no persiste los vectores entre arranques. Útil para pruebas.

## Consequences

**Positivas**
- El asistente puede responder preguntas en inglés sobre ofertas en español.
- Se practica el ciclo completo: embeddings, almacén vectorial, índices, umbrales, evaluación.
- Con `KnowledgeSearchPort` el cambio de estrategia es configuración.

**Negativas / riesgos**
- **Infraestructura:** PostgreSQL en `docker-compose.yml` y en las pruebas (Testcontainers) es `postgres:17-alpine`, que no incluye pgvector. Habrá que cambiar la imagen en ambos sitios (la imagen concreta se verifica en el plan). Que el PostgreSQL desplegado ofrezca pgvector **no está verificado**.
- **Migración:** la spec original prohibía migraciones; ahora se permiten **solo** para el almacén vectorial. La numeración se coordina con HU-22, que añade la suya.
- **Latencia y coste:** SC-002 («0 llamadas al LLM generativo por debajo del umbral») se mantiene; con embeddings remotos la latencia depende del proveedor y se mide y se reporta.
- **Más superficie de fallo:** un servicio más en la cadena de fallback y un umbral de similitud que calibrar.
- **Dos mecanismos que calibrar:** `k` de RRF y el umbral coseno, más el tamaño de `topK`. Más superficie de error si se configuran mal.
- **Sin degradación a BM25 si cae embeddings:** el asistente responde 503 (decisión a confirmar, ver «Fusión híbrida»). La híbrida aporta búsqueda exacta y línea base, no tolerancia a fallos del proveedor.
- **Pierden efecto «sin llamadas externas en menos de 50 ms»** (SC-002): cada consulta depende del proveedor de embeddings.
- **Cambiar de modelo de embeddings obliga a recalcular todos los vectores** (las dimensiones y el espacio cambian).
- Los textos que se envían al proveedor de embeddings son **solo públicos** (ofertas, categorías, políticas); no se envían datos de usuarios.
- **Clave dedicada:** `GEMINI_EMBEDDING_API_KEY` no tiene respaldo a `GEMINI_API_KEY`. Si falta, el módulo responde 503. La clave de embeddings sale de una **cuenta de Google distinta** a la del LLM (decisión del propietario, 2026-11-10), lo que aísla la cuota. Se confirma en el plan que la cuota de Gemini se contabiliza por cuenta o proyecto, para que el aislamiento sea real.

## Decisiones pendientes (se aplica la recomendada hasta que se indique otra cosa)

1. **Almacén vectorial:** pgvector en la PostgreSQL existente, que corre en **Railway** (recomendado) / base vectorial dedicada / `VectorStore` en memoria. Hay que **verificar que la PostgreSQL de Railway ofrece la extensión** pgvector antes de implementar.
2. **Detalles del proveedor de embeddings** (modelo, dimensión, artefacto de Spring AI, idiomas, cuota): se verifican en el plan.

## Fusión híbrida (MVP)

- **Fusión:** *Reciprocal Rank Fusion*: `score(d) = Σ 1 / (k + rango_r(d))` sobre los rankings de BM25 y de embeddings. `k` por defecto 60 `[INFERRED]`, a confirmar en el plan.
- **Rechazo temprano (FR-006):** el umbral `minScoreThreshold` se aplica a la **similitud coseno** del mejor fragmento de embeddings. Si ninguno lo supera, se responde «sin información» sin llamar al LLM, aunque BM25 haya encontrado palabras coincidentes. Así una coincidencia léxica marginal no provoca una llamada al LLM. `[INFERRED]`, a validar con FR-019.
- **BM25 no admite fragmentos por debajo del umbral:** solo reordena los candidatos. Un nombre exacto puede subir en el ranking, pero no se acepta si su similitud coseno está por debajo del umbral.
- **Fallo:** si el servicio de embeddings falla, se responde **503**, no una respuesta solo con BM25. Esto mantiene FR-009 y FR-017. Es una decisión a confirmar: la alternativa es un modo degradado solo con BM25, marcado de forma explícita en la respuesta.
- **Pruebas:** BM25 y la fusión RRF son puras y se testean sin red; los embeddings se sustituyen por un doble determinista.

## Out of scope

Entrenar o ajustar modelos de embeddings, un almacén vectorial distinto del elegido, reordenamiento con modelos (*reranking*), conversaciones multi-turno, fusión con más de dos rankings.

## References

- `specs/026-rag-knowledge/spec.md` (#77) y `specs/028-ai-fallback-config/spec.md` (capacidad `embedding`).
- [ADR-0007](0007-ai-provider-fallback-chain.md): cadena de fallback.
- [ADR-0006](0006-hybrid-monolith-and-services.md): una base de datos por dueño; el RAG no consulta otros servicios.
