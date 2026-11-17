# Mentorías — contrato REST

Base canónica: **`/api/v1/mentorships`** (servicio NestJS). Ver `state-machine.md` para estados.

## Enrutamiento

| Prefijo | Destino | Estado |
|---|---|---|
| `/api/v1/mentorships/**` | `mentorship-service` | Canónico |
| `/api/mentorships/**` | Monolito | **Deprecado**; congelado hasta el corte, luego se elimina |
| `/api/**` (resto) | Monolito | Sin cambios |

**[UNKNOWN]** El enrutamiento por prefijo requiere un punto de entrada (nginx/gateway). Hoy no está
verificado que exista; definirlo es prerequisito del corte.

## Autenticación

JWT emitido por el monolito, **RS256**. Cookie `access_token` o `Authorization: Bearer`. El servicio
valida firma y expiración con `JWT_PUBLIC_KEY`. Claims requeridos: `uid` (userId), `email`, `role`, `iss`
(`CUSTOMER` | `PROVIDER` | `ADMIN`). Ver `security.md`. Mutaciones con cookie exigen `X-XSRF-TOKEN`.

## Errores

Cuerpo uniforme: `{ "code": "...", "message": "...", "correlationId": "..." }`.
`X-Correlation-Id` se acepta en la petición y se devuelve siempre en la respuesta.

| HTTP | Cuándo | `code` |
|---|---|---|
| 400 | DTO inválido | `VALIDATION_ERROR` |
| 401 | Sin token o token inválido | `UNAUTHENTICATED` |
| 403 | Rol/propietario incorrecto o fuera de ventana | `FORBIDDEN` / `OUTSIDE_ACCESS_WINDOW` |
| 404 | No existe, **o pertenece a otro usuario** (no revelar existencia) | `NOT_FOUND` |
| 409 | Transición inválida / estado terminal | `INVALID_STATE_TRANSITION` |
| 422 | Progreso menor al actual | `PROGRESS_REGRESSION` |

## Endpoints

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /me` | CUSTOMER | Asignaciones propias, orden `accessStartsAt` desc. **No** crea asignaciones faltantes (el monolito sí lo hace; en el servicio solo las crea el evento). |
| `GET /:id` | Dueño / Provider de la oferta / ADMIN | Detalle. |
| `PATCH /:id/progress` | CUSTOMER dueño | `{ "progressPercent": 0..100 }`. Aplica monotonía y transiciones. |
| `GET /:id/access` | CUSTOMER dueño | Datos de conexión desde la BD local. **Sin llamadas a APIs externas.** |
| `GET /:id/resources` | CUSTOMER dueño | Recursos de la oferta si el estado y la ventana lo permiten. |
| `POST /` | PROVIDER dueño / ADMIN | Asignación manual (excepción; el flujo normal es por evento). |
| `POST /:id/cancel` | PROVIDER dueño / ADMIN | `{ "reason": "..." }` obligatorio. |
| `POST /:id/reschedule` | PROVIDER dueño / ADMIN | `{ "accessStartsAt", "accessEndsAt", "reason" }`. Solo `SCHEDULED`. |
| `POST /:id/live-room` | PROVIDER dueño / ADMIN | `{ "provider": "ZOOM" \| "MEET" \| "DISCORD" \| "CUSTOM_URL", "url"? }`. Con timeout 4 s y circuit breaker; si falla, ver HU-MNT-09. |
| `POST /offerings/:offeringId/resources` | PROVIDER dueño / ADMIN | Recurso `LINK`/`VIDEO`/`DOCUMENT` por URL (HU-MNT-05). |
| `PUT\|DELETE /resources/:id` | PROVIDER dueño / ADMIN | Edición y borrado. |
| `POST /resources/:id/file` · `GET /resources/:id/file` | PROVIDER · CUSTOMER con acceso | Archivo vía Storage Port (S3/local), HU-MNT-06. |

## Representación de `MentorshipAssignment`

```json
{
  "id": "uuid",
  "bookingId": "uuid",
  "customerId": "uuid",
  "offeringId": "uuid",
  "mode": "ASYNCHRONOUS | LIVE",
  "status": "SCHEDULED",
  "accessStartsAt": "2026-11-05T15:00:00Z",
  "accessEndsAt": "2026-11-05T17:00:00Z",
  "progressPercent": 0,
  "completedAt": null,
  "version": 0
}
```

No se exponen `zoomPasscode` ni secretos en listados; solo en `GET /:id/access`.
Las fechas son ISO-8601 UTC. Ningún identificador interno de base de datos distinto de los UUID.

## Diferencias con el monolito

| Aspecto | Monolito | Servicio |
|---|---|---|
| Prefijo | `/api/mentorships` | `/api/v1/mentorships` |
| Entidad JPA en la respuesta | Se devuelve `MentorshipAssignmentEntity` directa | DTO propio |
| `GET /me` | Crea asignaciones faltantes | Solo lee |
| Zoom | Cliente síncrono sin breaker | Breaker + `CUSTOM_URL` |
| Archivos | `BYTEA` en PostgreSQL | Storage Port |
