# HU-26 — Guía para explicar y recorrer lo que se hizo

> Para exponer el trabajo y para ver, archivo por archivo, todo lo que se agregó. Complementa `REPORTE.md` (qué y por qué) con un guion, una demo y los comandos para auditar.

## 1. En 30 segundos

SkillBridge ahora tiene un **asistente de conocimiento con RAG**: el usuario pregunta en lenguaje natural (español o inglés), el sistema busca los fragmentos más relevantes de las políticas, las ofertas y las categorías, y un LLM redacta la respuesta **citando sus fuentes**. Si la pregunta no se parece a nada del catálogo, responde «sin información» **sin gastar una llamada al LLM**. El mismo índice se reutiliza para que las **recomendaciones** ya no le mandan al LLM todo el catálogo, sino solo las ofertas más cercanas a lo que pidió el usuario.

## 2. Guion de exposición (≈ 10–12 min)

| # | Minutos | Qué decir | Qué mostrar |
|---|---|---|---|
| 1 | 1 | **El problema:** el LLM de recomendaciones leía todas las ofertas en cada petición (caro, lento, con límite de contexto) y no había forma de preguntar por políticas con respuestas verificables. | `AiRecommendationService.recommend` antes: `findAllActive()` y todo al LLM |
| 2 | 2 | **La idea:** buscar primero (recuperación) y generar después. Embeddings = texto → vector de 768 números; vectores cercanos = significados parecidos. | Diagrama de `REPORTE.md` §2 |
| 3 | 2 | **Búsqueda híbrida:** el embedding entiende el significado; BM25 acierta con términos exactos (nombres propios, siglas). Se fusionan con RRF. El umbral se aplica al coseno puro, no a la fusión. | `domain/service/knowledge/HybridRanker.java` |
| 4 | 1 | **Arquitectura hexagonal:** el dominio es Java puro; Gemini y pgvector están detrás de puertos. | Carpeta `application/port/out/` y `infrastructure/adapter/out/` |
| 5 | 3 | **Demo en vivo** (sección 3) | Navegador + una terminal |
| 6 | 1 | **Seguridad y honestidad:** sin mocks silenciosos (si Gemini cae, 503), anti-inyección de prompt, solo se indexa contenido público, las citas se validan contra el contexto. | `KnowledgePromptBuilder`, `SourceCitationValidator` |
| 7 | 1 | **Qué falló y se corrigió** (sección 5): cuatro bugs que solo aparecieron al probar con la pila real. | `REPORTE.md` §5 |
| 8 | 1 | **Resultados y límites:** 20/20 con preselección de 10 sobre 46 ofertas; el umbral 0.5 es provisional y la calibración queda pendiente. | Tabla de `progress.md` |

## 3. Demo en vivo (paso a paso)

Requisitos: Docker corriendo, `.env` con las dos claves de Gemini, `GEMINI_MODEL=gemini-3.1-flash-lite` y `JWT_GENERATE_EPHEMERAL_KEYS=true`.

1. `docker compose up --build -d` y abrir http://localhost:8088 (regístrate o inicia sesión).
2. **Catálogo grande:** `docker compose exec -T postgres psql -U skillbridge -d skillbridge < scripts/dev/seed-rag-offerings.sql`, luego `docker compose exec -T redis redis-cli FLUSHALL` y `docker compose restart backend` (carga 43 ofertas de prueba y las indexa).
3. **Pestaña de conocimiento:** «¿Con cuánta anticipación debo cancelar una reserva?» → respuesta con la tarjeta de fuente y su porcentaje. Repite en inglés: «How much notice do I need to cancel a booking?».
4. **Rechazo temprano:** «¿Cómo cocino arroz?» → aviso neutro, sin fuentes.
5. **Recomendaciones con título:** «Quiero proteger mi web de ataques de inyección SQL» → «Seguridad de aplicaciones web (OWASP)» arriba. Muestra el log: `docker compose logs backend | findstr Preselecci` (qué 15 de las 46 ofertas vio el LLM).
6. **Índice y vectores:** `select source_type, count(*) from knowledge.knowledge_chunks group by 1;` y `select source_id, vector_dims(embedding) from knowledge.knowledge_chunks limit 5;` (768).
7. **Sincronización:** desactiva una oferta (`update offerings set active=false where id='aaaaaaaa-0000-0000-0000-000000000022';`, `redis-cli FLUSHALL`, reinicia) y comprueba que su fragmento desaparece del índice; reactívala y vuelve.

Limpieza del catálogo de prueba: las dos sentencias `DELETE` al inicio de `scripts/dev/seed-rag-offerings.sql`.

## 4. Cómo ver TODO lo que se agregó

Los comandos son de solo lectura. La rama conserva el historial de la HU-26 y de trabajo previo de otras historias; estos filtros dejan solo la HU-26.

```bash
# Commits de la HU-26
git log main..HEAD --no-merges --since=2026-11-09 -i -E --grep='[(]rag[)]|HU-26' --oneline

# Los 5 commits de la última tanda (arreglos, híbrida, recomendaciones, pruebas, configuración)
git log --stat -5 --format='%n%h %s' --skip=1

# Una pieza concreta con su historia
git log --oneline -- backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/knowledge/PgVectorKnowledgeAdapter.java

# Todo el código nuevo del RAG (solo archivos añadidos)
git diff --diff-filter=A --name-only main HEAD -- 'backend/src/main/java/**/knowledge/**' 'backend/src/test/java/**/knowledge/**' specs/026-rag-knowledge scripts/dev
```

**Cifras de la HU-26** (33 commits, sin contar los commits finales de documentación y refactor; el corpus de políticas llegó en otro commit mixto):

| Área | Archivos | Líneas |
|---|---|---|
| Backend código | 39 | +3.950 / −192 |
| Backend tests | 28 | +6.622 / −36 |
| Frontend | 4 | +1.290 / −61 |
| Specs | 10 | +885 / −92 |
| Docs | 6 | +266 / −44 |
| Migración (V14), recursos, compose/env, scripts | 6 | +149 / −12 |
| **Total** | **93 (82 nuevos, 28 de ellos tests)** | **+13.162 / −437** |

**Fases del trabajo** (por tipo de commit, en orden):

1. *Especificación:* `spec.md`, `plan.md`, `tasks.md`, ADR-0008 (SDD).
2. *Dominio puro:* coseno, BM25, RRF, modelos (`93d7557`, `0473ff0`).
3. *Puertos y adaptadores:* corpus, pgvector, embeddings Gemini, respuesta Gemini (`e0c086d` … `0c6fbb3`).
4. *Caso de uso, endpoint e índice:* `AskKnowledgeService`, controlador, `KnowledgeIndexService`.
5. *Frontend:* cliente HTTP y pestaña de conocimiento.
6. *Esta tanda (5 commits):* arreglos de integración, búsqueda híbrida y arranque diferencial, preselección de recomendaciones, pruebas de flujo, configuración.
7. *Documentación:* `REPORTE.md`, esta guía, secciones en `docs/`.

## 5. Lo que conviene contar: bugs que solo salieron probando de verdad

1. `source_id UNIQUE` chocaba con los fragmentos ES/EN del mismo documento → habría dejado el índice vacío.
2. Nunca existía un `EmbeddingPort` en la app real (el adaptador dependía de un bean que el propio `application.yml` excluye).
3. La carga inicial fallaba por una auto-invocación que se saltaba el proxy transaccional.
4. Cada reinicio recalculaba todos los embeddings, y desactivar una oferta no la quitaba del índice.

Mensaje: las pruebas unitarias en verde no bastaron; la prueba con Docker, pgvector y Gemini reales encontró lo que importaba.

## 6. Preguntas probables

| Pregunta | Respuesta honesta |
|---|---|
| ¿Por qué no solo pasarle todo al LLM? | Costo, latencia y límite de contexto crecen con el catálogo. Con 3 ofertas no importa; con cientos sí. La preselección con N=10 sobre 46 mantuvo 20/20 y enviaba 78 % menos. |
| ¿Por qué BM25 además de embeddings? | Los embeddings fallan con términos raros o exactos; BM25 los cubre. Con el embedding falso de pruebas la híbrida empata con BM25; la comparación con Gemini real sigue pendiente. |
| ¿Cómo se evita que el LLM invente fuentes? | Solo se aceptan citas cuyo id estaba en el contexto enviado (`SourceCitationValidator`). |
| ¿Y la inyección de prompt? | `KnowledgePromptBuilder` sanitiza delimitadores, pone la pregunta en un bloque de usuario y usa instrucciones de sistema fijas. Hay pruebas, no una garantía absoluta. |
| ¿Qué pasa si Gemini cae? | 503 con ProblemDetail; nunca respuestas simuladas. En recomendaciones se usa el catálogo completo. |
| ¿Está calibrado el umbral? | No: 0.5 es provisional. El barrido con embeddings reales mostró que ningún umbral separa limpiamente; 0.65–0.70 es el mejor equilibrio observado. Decisión pendiente (T030). |
| ¿Cuánto cuesta cada petición? | Un embedding de la pregunta siempre; una llamada generativa solo si pasa el umbral. Los documentos se vectorizan una vez (y al cambiar). |
| ¿Qué falta? | T002 (extensión `vector` en Railway), T003 (cuota de Google), T030 (umbral), T039 (enmienda de la constitución), T041 (recorrido completo del quickstart). |

## 7. Glosario mínimo

- **Embedding:** vector numérico que representa el significado de un texto (aquí 768 dimensiones, `gemini-embedding-001`).
- **Similitud coseno:** qué tan alineados están dos vectores (1 = igual, 0 = sin relación).
- **BM25:** puntuación léxica clásica por palabras compartidas, ponderando las raras.
- **RRF (Reciprocal Rank Fusion):** combina varios rankings sumando 1/(k + posición); no usa los puntajes originales.
- **HNSW:** índice aproximado de vecinos más cercanos de pgvector.
- **RAG:** recuperar fragmentos relevantes y pedirle al LLM que responda solo con ellos.

## 8. Relación con la paginación

La paginación de ofertas y reservas vive en su propia rama y su propio PR (`feature/pagination-offerings-bookings`); no forma parte de este trabajo. Se tocan zonas distintas del código (listados, no el índice del RAG), pero ambos PRs modifican `docs/API.md` en secciones distintas, así que el segundo en mergearse puede requerir resolver un conflicto trivial en ese archivo.
