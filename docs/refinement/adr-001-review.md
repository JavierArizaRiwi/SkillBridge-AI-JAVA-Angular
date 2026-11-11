# Revisión de ADR-001 (abstracción de proveedores de IA)

Fecha de la revisión: 2026-11-09. Alcance: contrastar `docs/adr/ADR-001-AI-PROVIDER-ABSTRACTION.md` y su evidencia (`docs/adr/AI_PROVIDER_COMPARISON.md`, `docs/history/AI_PROVIDER_BASELINE.md`, `docs/history/HU-15-ISSUES_AND_BLOCKERS.md`) con el código actual. Marcas: `[VERIFIED]` comprobado en el repo en esta sesión · `[INFERRED]` deducción · `[UNKNOWN]` no se pudo confirmar.

**No se modificó el ADR.** Un ADR es un registro de decisión; las correcciones propuestas van como anotación o como ADR sucesor (ver §5).

## 1. Qué decide el ADR (y sigue siendo válido)

Un puerto (`AiRecommendationPort`) con un adaptador por proveedor, activado por `@ConditionalOnProperty(app.ai.provider)`, sobre el `ChatClient` de Spring AI. `[VERIFIED]` en `GeminiAiAdapter.java:23` (`gemini`, con `matchIfMissing`) y `OpenAiAiAdapter.java:18` (`openai`). La idea central —aislar al dominio del proveedor— se cumple y es la base de HU-27/28.

## 2. Afirmaciones del ADR que el código contradice

| # | El ADR dice | Realidad `[VERIFIED]` | Gravedad |
|---|---|---|---|
| 1 | `app.ai.timeout-seconds: 10` protege las llamadas (§Decision.3) | `getTimeoutSeconds()` **no se usa** en `GeminiAiAdapter` ni `OpenAiAiAdapter`. Solo aparece en la propiedad (`AiProviderProperties.java:11`), en `application.yml:147` y como **texto** del mensaje de error de OpenAI (`OpenAiAiAdapter.java:69`: "timeout: Ns"). Solo ElevenLabs aplica timeouts reales (`ElevenLabsTextToSpeechAdapter.java:27-28`). | Alta: la configuración sugiere una protección que no existe |
| 2 | "A/B Testing: se pueden enrutar peticiones distintas a proveedores distintos" (§Consequences) | Con `@ConditionalOnProperty` **solo hay un bean activo**; no hay enrutado por petición. El propio diseño lo impide. | Media: promesa imposible de cumplir con esta arquitectura |
| 3 | "Cambio en tiempo de ejecución sin recompilar" | Es cambio **al arrancar** (requiere reiniciar). No hay cambio en caliente. | Baja: matiz |
| 4 | Mapeo de errores: `IllegalArgumentException → INVALID_INPUT`, `RuntimeException → UNAVAILABLE` (§Decision.4) | Gemini hoy **clasifica por texto del mensaje** (`GeminiAiAdapter.java:121-132`: busca "timeout", "401", "429", "quota"…) y produce `TIMEOUT`, `RATE_LIMITED`, `INVALID_INPUT`, `UNAVAILABLE`, `INTERNAL_ERROR`. OpenAI sigue colapsando todo a `UNAVAILABLE`. Los dos proveedores **no son consistentes**, contra el criterio de validación "manejo de errores consistente". | Alta: clasificar por substring es frágil (mismo defecto que RAG-001) |
| 5 | "CI/CD valida que exactamente un proveedor esté habilitado" (§Mitigation) | **No existe** tal validación: `ci.yml` no menciona proveedor de IA. Con un valor desconocido de `app.ai.provider` no hay bean de `AiRecommendationPort` y el arranque falla sin mensaje claro. | Media |
| 6 | "Ambos adaptadores pasan sus pruebas (8 cada uno)" | Gemini tiene **13** pruebas y OpenAI **8** (`@Test` en cada clase). | Baja: dato obsoleto |
| 7 | "Cero degradación de rendimiento" | No hay medición en el repo que lo respalde. `[UNKNOWN]` | Baja |
| 8 | Alcance: solo recomendaciones | Hoy hay además `VisionPort`, `SpeechToTextPort` (Gemini y Groq, `app.ai.audio-provider`) y `TextToSpeechPort` (ElevenLabs/NoOp, `spring.ai.tts.enabled`). El ADR no menciona ninguno de ellos (0 coincidencias de "vision/speech/groq/elevenlabs"). | Media: el ADR describe una parte del sistema |

## 3. La evidencia que respalda la recomendación es débil

La recomendación del ADR ("Gemini en desarrollo, gpt-3.5-turbo en producción") sale de `AI_PROVIDER_COMPARISON.md`. Su método, tal como está escrito:

- **Latencia** (§2): *10 peticiones por proveedor*, un solo prompt, 2 ofertas en el catálogo, red local de Docker. No cita herramienta, script ni datos crudos. Con n = 10, los **P95 y P99 no tienen significado estadístico** (P99 con 10 muestras es el máximo).
- **Calidad** (§4): puntuación de 1 a 10 asignada por "SkillBridge Development Team" sobre ese único prompt, sin evaluador identificado, sin ciego ni rúbrica verificable. "Sin alucinaciones 10/10" con solo 2 ofertas es trivial.
- **GPT-3.5-turbo**: la tabla resumen (§10) da latencia 1200 ms y calidad 8,0 para este modelo, pero **no encontré ninguna prueba descrita** para él en las secciones de latencia y calidad (solo Gemini y gpt-4-turbo). `[INFERRED]` que son estimaciones.
- **Fiabilidad** "99,9 % / 99,95 %" (§10): no se puede medir con 10 peticiones; `[INFERRED]` que proviene de SLAs publicitados, sin fuente citada.
- **Costes**: cálculo aritmético con una lista de precios supuesta y 10.000 peticiones/día. Los precios cambian; `[UNKNOWN]` si siguen vigentes. Me abstengo de afirmar cifras actuales.
- **Inconsistencia interna**: la cabecera del comparativo recomienda **OpenAI GPT-4 Turbo**; el ADR resume la recomendación como **gpt-3.5-turbo en producción**; y la configuración real usa **Gemini por defecto** (`application.yml:142`).
- `HU-15-ISSUES_AND_BLOCKERS.md` indica que las pruebas de adaptadores son con Mockito y `MockAiAdapter` ("nunca llames a APIs reales" en tests), y `application.yml` trae `OPENAI_API_KEY` por defecto `dummy-key`. `[INFERRED]` que las mediciones se hicieron fuera de la suite, a mano; no hay rastro reproducible.

**Conclusión sobre la evidencia:** sirve como orientación cualitativa de la época, pero **no justifica** una recomendación de proveedor ni de modelo. No debe citarse como prueba en decisiones nuevas (p. ej. HU-27/28).

## 4. Problemas de forma

- **Numeración colisionada**: existe `docs/adr/0001-modular-monolith-first.md` y también `ADR-001-…`. Dos "001" distintos en la misma carpeta. El resto de ADR usa `000N-`.
- **Estado desactualizado**: marca "Phase 3 (In Progress)" y "Next Review: 2026-11-07", fecha ya pasada. HU-15 (issue #9) está cerrada.
- **Referencia colgante**: `docs/ISSUES_AND_BLOCKERS.md` (§Related Documents) nunca existió con ese nombre; probablemente se refería a `docs/history/HU-15-ISSUES_AND_BLOCKERS.md` `[INFERRED]`.
- **Fase 4 (A/B, métricas por proveedor)**: no encontré implementación en el código, y el A/B contradice la arquitectura (hallazgo 2).
- **Menciona "Claude, Cohere, Llama" como futuro**; las specs 027/028 sacan expresamente a Claude/Mistral/Cohere de alcance. Conviene alinear.

## 5. Impacto en HU-27/28 y propuesta

La arquitectura de ADR-001 (**exactamente un adaptador activo por propiedad**) es lo que HU-27 sustituye por un **adaptador compuesto con cadena ordenada**. La spec 027 (FR-010) ya exige "actualizar ADR-001 o crear un ADR nuevo".

Propuesta (para decidir, nada aplicado):
1. **Nuevo ADR-0007**, "Cadena de fallback de proveedores de IA", que *supersede parcialmente* a ADR-001 en la selección de adaptador y cubre los cuatro puertos (chat, visión, STT, TTS), la clasificación de errores por tipo (no por texto), timeouts aplicados en el cliente y el circuito de HU-28.
2. **Anotación en ADR-001** (sin reescribir el cuerpo): estado "Parcialmente sustituido por ADR-0007", y una sección *Errata 2026-11-09* con los hallazgos 1–8 y la nota de que la evidencia es orientativa.
3. **Corregir la referencia colgante** dentro de esa errata, no en el cuerpo.
4. **Renombrar** a un número no colisionante solo si se acepta actualizar los enlaces (`CLAUDE.md`, `INTEGRATIONS.md`, specs 027/028); si no, dejar el nombre y documentar la colisión.
5. **Repetir la comparación de proveedores solo si importa decidir un modelo.** Debería tener ≥ 100 muestras por proveedor, varios prompts, rúbrica de calidad con evaluador ciego, y datos crudos versionados. Con la cadena de fallback de HU-27/28 la pregunta "¿cuál es el mejor?" pasa a ser "¿en qué orden los pongo?", que se decide con medición propia en el entorno real.

## 6. Hallazgos que conviene convertir en trabajo

| Hallazgo | Dónde atenderlo |
|---|---|
| Timeout no aplicado (#1) | HU-27 (mecanismo) y HU-28 (configuración); ya cubierto por spec 026 FR-008 y 027 FR-002 |
| Clasificación de errores por texto (#4) | HU-27: clasificar por tipo de excepción/código HTTP |
| Validación de configuración al arranque (#5) | HU-28 FR-006 (fail-fast) |
| Alcance multimodal sin ADR (#8) | ADR-0007 |

## 7. Estado de aplicación (2026-11-09)

Aplicado por decisión del propietario: (1) anotación y errata en ADR-001; (2) [ADR-0007](../adr/0007-ai-provider-fallback-chain.md) en estado *Proposed*; (3) [índice de ADR](../adr/README.md) que aclara la numeración. El cuerpo original de ADR-001 no se modificó. Los defectos de código (§6) siguen pendientes de HU-27 y HU-28.
