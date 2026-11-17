# Contrato: `POST /api/ai/knowledge/ask`

Único endpoint externo nuevo de HU-26. Se origina en `spec.md` FR-001 a FR-003 y en los edge cases. Se implementa en `infrastructure/adapter/in/rest/` como parte de `AiController` o de un controlador hermano, y llama solo al puerto de entrada `AskKnowledgeUseCase` (constitución Art. I.2).

## Autenticación

- Cookie `access_token` (HttpOnly) o `Authorization: Bearer`.
- Sin token válido → `401 Unauthorized` antes de llegar a la aplicación (FR-001, SC-001).
- Escritura con cookie → requiere `X-XSRF-TOKEN` (CSRF, constitución Art. III.2). Este endpoint es `POST`, así que aplica.
- Cualquier rol autenticado puede llamarlo (spec, Assumptions: sin roles especiales).

## Request

```http
POST /api/ai/knowledge/ask
Content-Type: application/json
X-Correlation-Id: <opcional>
X-XSRF-TOKEN: <si la petición usa cookie>

{ "question": "¿Con cuánta anticipación puedo cancelar una reserva?" }
```

| Campo | Regla |
|---|---|
| `question` | Obligatorio. Después de quitar espacios: mínimo 1 carácter, máximo 500 (FR-002). |

## Response `200 OK` (FR-003)

```json
{
  "answer": "string",
  "hasSufficientContext": true,
  "sources": [
    { "sourceId": "policy-cancellation", "title": "string", "snippet": "string", "score": 0.82 }
  ]
}
```

- `score` es la similitud coseno normalizada a [0, 1] del fragmento citado. No es la puntuación RRF, para que el umbral y el score sean comparables por el cliente.
- `sources` solo contiene fragmentos citados en `answer` (FR-007, SC-003).

Caso «sin información» (FR-006), sin llamada al LLM:

```json
{ "answer": "No dispongo de información suficiente sobre esa consulta en la plataforma.", "hasSufficientContext": false, "sources": [] }
```

El texto exacto del mensaje se fija en la implementación y se testea con MockMvc.

## Errores (formato RFC 7807 `ProblemDetail`, `GlobalExceptionHandler`)

| Caso | HTTP | Cuándo |
|---|---|---|
| Validación de `question` | `400` | Vacía, solo espacios o > 500 caracteres (FR-002). No se procesa el corpus ni se llama a ningún proveedor. |
| Sin autenticación | `401` | FR-001. |
| Embeddings no disponibles | `503` | Falla o timeout del proveedor de embeddings, o `GEMINI_EMBEDDING_API_KEY` ausente (FR-017). Nunca respuesta solo con BM25. |
| Timeout o indisponibilidad del LLM generativo | `503` | `AiProviderException` con `TIMEOUT` o `UNAVAILABLE` (FR-008, FR-009). |
| Límite de cuota del LLM | `429` | `AiProviderException` por rate limit (edge case de indisponibilidad). |

Ninguna respuesta de error incluye la pregunta completa ni secretos en logs (FR-010). Los logs registran `correlationId`, número de fragmentos, longitudes y tipo de error.

## Garantías verificables

- Pregunta fuera de dominio → `200` con `hasSufficientContext: false` y **0 llamadas** al LLM generativo (SC-002). Esto se prueba con un doble espía del puerto LLM.
- Ninguna respuesta `200` contiene datos fijos simulados (SC-005).
