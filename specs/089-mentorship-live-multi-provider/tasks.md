# Tasks: Salas de mentoría en vivo multi-proveedor (HU-MNT-07)

**Spec**: `specs/089-mentorship-live-multi-provider/spec.md`
**Plan**: `specs/089-mentorship-live-multi-provider/plan.md`
**Destino**: `BackendNest.js/` (NestJS 12, ESM, TypeORM, Vitest).

## Phase 1 - Scaffold y persistencia

- [x] **T001** Reutilizar `BackendNest.js/` (no crear un servicio nuevo) y añadir dependencias (`jsonwebtoken`, Testcontainers) y scripts (`test:db`).
- [x] **T002** [P] Definir entidades TypeORM `MentorshipAssignmentEntity`, `LiveRoomEntity` y `OutboxEventEntity` con enums.
- [x] **T003** [P] Añadir migración SQL inicial (`1700000000000-InitialSchema`) y `.env.example` del servicio.
- [x] **T004** Implementar `DatabaseModule` (TypeORM `forRootAsync`), `data-source.ts` y configuración tipada de entorno.

## Phase 2 - Seguridad y contrato común

- [x] **T005** Implementar `JwtAuthGuard` RS256, `RolesGuard`, decoradores `@Roles` y `@CurrentUser`.
- [x] **T006** [P] Implementar middleware de `X-Correlation-Id`.
- [x] **T007** [P] Implementar `DomainError`, filtro global `{ code, message, correlationId }` y cifrado AES-256-GCM.

## Phase 3 - Proveedores

- [x] **T008** Definir `LiveMeetingPort`, `ProviderRoomResult` y utilidades de topic/nombre.
- [x] **T009** [P] Implementar `ZoomLiveMeetingAdapter` con OAuth, creación idempotente, URL de anfitrión y firma de participante.
- [x] **T010** [P] Implementar `GoogleMeetLiveMeetingAdapter` con Calendar API, `conferenceData` y reconciliación por `assignmentId`.
- [x] **T011** [P] Implementar `DiscordLiveMeetingAdapter` con canal de voz e invitación con expiración.

## Phase 4 - Caso de uso y API

- [x] **T012** Definir `LiveRoomRepository` y su implementación TypeORM con bloqueo asesor y outbox atómico.
- [x] **T013** Implementar `LiveRoomService`: creación, idempotencia, autorización, acceso y reconciliación.
- [x] **T014** Implementar `LiveRoomController` con `POST /api/v1/mentorships/:id/live-room` y `GET /api/v1/mentorships/:id/access`.
- [x] **T015** Registrar módulos, guards, filtros, `ValidationPipe` y middleware en `app.module.ts`.

## Phase 5 - Pruebas

- [x] **T016** [P] Pruebas de `LiveRoomService`: idempotencia, concurrencia, estados, roles y acceso (`test/live-room.service.spec.ts`).
- [x] **T017** [P] Pruebas de Zoom con servidor HTTP simulado.
- [x] **T018** [P] Pruebas de Google Meet con servidor HTTP simulado.
- [x] **T019** [P] Pruebas de Discord con servidor HTTP simulado (`test/adapters.spec.ts`).
- [x] **T020** Pruebas de redacción: secretos ausentes en respuesta de participante, eventos y errores.
- [x] **T024** [P] Pruebas HTTP de autenticación RS256, RBAC, CSRF, correlación y contrato de errores (`test/http-api.spec.ts`).
- [x] **T025** Pruebas de persistencia PostgreSQL con Testcontainers: migración, transacción atómica sala+outbox y bloqueo asesor concurrente (`npm run test:db`).

## Phase 6 - Verificación

- [x] **T021** Ejecutar `npm run build`.
- [x] **T022** Ejecutar `npm test` y `npm run lint`.
- [x] **T023** Revisar que no se modificaron archivos de `backend/`, `frontend/`, `audit-service/` ni `.env`.
