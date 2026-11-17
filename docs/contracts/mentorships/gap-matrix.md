# Mentorías — matriz de brechas (monolito vs. issues #83–#91)

Estado al 2026-11-09. `[VERIFIED]` = confirmado en código/issue en esta sesión.

| # | HU | Espera el servicio | Monolito hoy | Brecha | Responsable |
|---|---|---|---|---|---|
| 83 | MNT-01 base Nest + Prisma | BD aislada | Tablas `mentorship_*` en la BD del monolito (V8–V10) `[VERIFIED]` | Migrar datos o empezar vacío **[DECIDIR]** | Equipo Nest |
| 84 | MNT-02 aprovisionamiento ECST | `PaymentApproved` con `customerId, customerEmail, offeringId, offeringTitle, providerId, scheduledAt, durationHours, mode` | Publica solo `bookingId` `[VERIFIED]` | **Enriquecer evento** + `mode`/`durationHours` en `Offering` | Monolito (este trabajo) |
| 84 | — | Cola `mentorship.payment.approved.queue` | Solo `booking.payment.approved.queue` `[VERIFIED]` | Nueva cola enlazada a la misma clave | Equipo Nest declara la cola |
| 85 | MNT-03 ciclo de vida | `GET /api/v1/mentorships/me`, `PATCH .../progress` | `/api/mentorships/...` `[VERIFIED]` | Ruta distinta; `GET /me` del monolito crea asignaciones | Contrato `rest-api.md` |
| 85 | — | `JWT_PUBLIC_KEY` (asimétrico) | HS256 con `JWT_SECRET` `[VERIFIED]` | **Migrar a RS256** | Monolito (este trabajo) |
| 85 | — | Transiciones con bloqueo terminal | Sin matriz de transiciones; progreso puede bajar `[VERIFIED]` | `state-machine.md` | Equipo Nest implementa |
| 86 | MNT-04 acceso estricto | 404 si no es dueño; ventana | Equivalente en `owned()` `[VERIFIED]` | Ninguna funcional | — |
| 87 | MNT-05 recursos URL | CRUD por URL | `MentorshipResourceController` | Reimplementar | Equipo Nest |
| 88 | MNT-06 archivos | Storage Port S3/local | `file_data BYTEA` en PostgreSQL | Migrar archivos | Equipo Nest |
| 89 | MNT-07 multi-proveedor | Zoom/Meet/Discord | Solo Zoom (`ZoomMeetingClient`) `[VERIFIED]` | Nuevo | Equipo Nest |
| 90 | MNT-08 outbox → Kafka | `BusinessEvent<T>` en `booking-events` | Envelope existe; depende de Jackson | Contrato JSON en `events.md` | Equipo Nest |
| 90 | — | `audit-service` persiste eventos `Mentorship*` | **[UNKNOWN]** cómo trata tipos nuevos | Aceptar tipos nuevos y desconocidos | Audit (este trabajo) |
| 91 | MNT-09 resiliencia | Breaker + `CUSTOM_URL`; lectura local | Llamada Zoom síncrona sin breaker | Nuevo | Equipo Nest |
| 68 | Plataforma de mentorías | Estados, cancelación, reembolso, ausencia | Definidos solo parcialmente | Políticas abiertas en `state-machine.md` | Product Owner |

## Deuda aceptada en el monolito (se elimina con el corte)

- `MentorshipController` accede a `Jpa*Repository` y devuelve entidades JPA directamente `[VERIFIED]`.
  Viola la regla hexagonal de `AGENTS.md` §1 y la de controladores de `CLAUDE.md`.
- No se corrige: el código se retira al migrar. Un defecto de este módulo se reporta enlazado a #68.

## Plan de corte (propuesto)

1. Monolito publica `PaymentApproved` v2 y migra a RS256 (cambios aditivos).
2. El servicio Nest consume en modo sombra (crea asignaciones, no sirve tráfico).
3. Comparar asignaciones monolito vs. servicio durante una ventana.
4. Enrutar `/api/v1/mentorships/**` al servicio; el frontend cambia de base.
5. Congelar y luego eliminar `Mentorship*` del monolito (nueva migración que retira tablas; nunca editar V8–V10).

## Estado de implementación en el monolito (2026-11-09)

| Cambio | Estado | Verificación |
|---|---|---|
| JWT RS256 (`JwtService`, claim `uid`, sin clave por defecto, `legacy-secret` opcional) | Implementado | `JwtServiceTest` (10 tests) + suite del backend: ejecutados, pasan |
| `Offering.mode` y `Offering.durationHours` (dominio, entidad, API, migración `V13`) | Implementado | `OfferingMentorshipFieldsTest` (7 tests): ejecutados, pasan. **Migración V13 y mapeo JPA no ejecutados**: requieren Docker/PostgreSQL |
| `PaymentApproved` v2 (`PaymentApprovedAssembler`, pago directo y webhook de Stripe) | Implementado | `PaymentApprovedAssemblerTest` y `PaymentApprovedJsonContractTest`: ejecutados, pasan. **Publicación real en RabbitMQ no ejecutada** |
| `audit-service` tolerante a `eventType` desconocidos y con actor en primer nivel | Implementado | `BusinessEventDeserializationTest` (5 tests): ejecutados, pasan. Persistencia en MongoDB no ejecutada (requiere Docker) |
| Formulario de oferta en Angular con `mode`/`durationHours` | **Pendiente** | — |
| Frontend: cambio de base a `/api/v1/mentorships` y enrutamiento por prefijo | **Pendiente** (depende del corte) | — |
| Rotación de claves JWT (lista por `kid`) | **Pendiente** | — |
| Declarar `mentorship.payment.approved.queue` | Equipo Nest (el monolito no la crea) | — |

Notas de comportamiento:
- Si faltan reserva, oferta o cliente, el monolito emite `PaymentApproved` en **forma mínima v1** (solo
  `bookingId`) y registra un aviso, para no bloquear su propio flujo. El servicio Nest debe tratar un mensaje v1
  como estructuralmente inválido (→ `mentorship.payment.dlq`), lo que lo hace visible y reprocesable.
- Los tests de la suite que requieren Docker (`BookingPersistenceAdapterTest`, `AuditEventRepositoryTest`,
  `BookingCancellationIT`) **no se ejecutaron en esta sesión**: no hay demonio de Docker activo.
