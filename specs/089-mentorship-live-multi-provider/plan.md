# Implementation Plan: Salas de mentoría en vivo multi-proveedor (HU-MNT-07)

**Feature**: `specs/089-mentorship-live-multi-provider/spec.md`
**Branch**: `feature/HU-MNT-07-Mentorías-en-vivo-multi-proveedor`
**Created**: 2026-11-10
**Status**: Implementación sobre `BackendNest.js` (NestJS 12 + TypeORM + Vitest)

## 1. Contexto técnico

La implementación vive dentro del proyecto NestJS existente **`BackendNest.js/`** (no se crea un
servicio nuevo ni una carpeta paralela). Ese proyecto ya aporta la base que MNT-07 reutiliza:

- **ESM** (`"type": "module"`, `module: nodenext`): los imports relativos terminan en `.js` y no
  existe `__dirname` (se usa `import.meta.dirname` si hiciera falta).
- **TypeORM** ya configurado (`@nestjs/typeorm` 12, `typeorm` 1.x, driver `pg`) con
  `DatabaseModule` y variables `DB_*`.
- **Vitest** como runner (no Jest): los mocks son `vi.fn()`.
- `class-validator`, `@nestjs/config`, `@nestjs/swagger`, `@nestjs/microservices`, `oxlint` y
  `prettier` ya disponibles.

La migración respecto al borrador original sustituye **Prisma por TypeORM** y **Jest por Vitest**.
No se modifican `backend/` ni `audit-service/`; no hay dependencias de compilación entre módulos.

## 2. Arquitectura

```text
BackendNest.js/
├── src/
│   ├── main.ts
│   ├── app.module.ts          # ConfigModule global, DatabaseModule, AuthModule, filtros
│   ├── config/                # configuración tipada (env → objeto)
│   ├── database/
│   │   ├── database.module.ts # TypeORM forRootAsync + migraciones
│   │   ├── data-source.ts     # entidades + migraciones reutilizables
│   │   └── migrations/        # 1700000000000-InitialSchema
│   ├── auth/                  # JwtAuthGuard RS256, RolesGuard, @Roles, @CurrentUser
│   ├── common/                # DomainError, filtro {code,message,correlationId}, cifrado, middleware
│   └── mentorship/
│       ├── entities/          # MentorshipAssignment, LiveRoom, OutboxEvent (TypeORM)
│       ├── mentorship/        # MentorshipModule (compone LiveRoomModule)
│       └── live-room/
│           ├── domain/        # enum, tipos y reglas puras
│           ├── ports/         # LiveMeetingPort, LiveRoomRepository
│           ├── adapters/      # Zoom, Google Meet, Discord, TypeORM repository
│           ├── dto/
│           ├── live-room.service.ts
│           ├── live-room.controller.ts
│           └── live-room.module.ts
└── test/                      # helpers de servidores HTTP simulados + specs
```

## 3. Decisiones técnicas

| Tema | Decisión |
|---|---|
| Endpoint | `POST /api/v1/mentorships/:id/live-room` con `{ provider }`. |
| Proveedores | `ZOOM`, `MEET`, `DISCORD`. `CUSTOM_URL` queda para MNT-09. |
| Persistencia | TypeORM (`DataSource` inyectado). Entidades `mentorship_assignments`, `live_rooms`, `outbox_events`; migración explícita en SQL. |
| Idempotencia | Restricción única `live_rooms.assignmentId`; bloqueo asesor `pg_advisory_xact_lock(hashtextextended(...))` por asignación; los adaptadores reconcilian por identificador de asignación antes de crear. |
| Transacción | La llamada externa y la persistencia de sala + outbox ocurren dentro de una transacción (`lock_timeout` acotado). |
| Credenciales de proveedor | Variables de entorno. Si un proveedor no está configurado, solo ese proveedor responde `503`. |
| Secretos de sala | AES-256-GCM con `LIVE_ROOM_ENCRYPTION_KEY`; nunca se registran en logs ni eventos. |
| Zoom | OAuth Server-to-Server; `start_url` para anfitrión, `join_url`/passcode/firma Web SDK para participante. |
| Google Meet | Evento de Calendar con `conferenceData`; `requestId = assignmentId`; búsqueda por `extendedProperties.private.assignmentId` para reconciliar. |
| Discord | Canal de voz temporal e invitación con expiración hasta `accessEndsAt`; nombre no sensible. |
| Errores | Cuerpo `{ code, message, correlationId }`; `400`, `401`, `403`, `404`, `409`, `503`, `504`. |
| Observabilidad | `X-Correlation-Id` en request/response, logs y outbox. |
| Pruebas | Vitest + servidores HTTP locales efímeros para Zoom/Google/Discord; Testcontainers PostgreSQL para persistencia; sin credenciales reales. |

## 4. Constitution Check

- **Artículo I/II**: la lógica de proveedores vive en adaptadores; el dominio (`live-room/domain`)
  no importa SDKs ni TypeORM.
- **Artículo III**: no se manejan PAN/CVV; JWT RS256; secretos cifrados y redactados.
- **Artículo IV**: `BusinessEvent` y `X-Correlation-Id` en el outbox y los logs.
- **Artículo V**: un fallo de proveedor produce `503`/`504`; no hay mock en producción.
- **Artículo VII**: `BackendNest.js/` es independiente; no hay dependencia de compilación con
  `backend/` ni `audit-service/`.
- **Artículo VIII**: se actualizan este plan, la spec y las tareas; los contratos se respetan.

## 5. Riesgos y pendientes

1. **Identidad de Google Meet**: la implementación soporta cuenta de servicio con
   `GOOGLE_CALENDAR_ID`; si el producto exige que el Provider sea el organizador, hará falta OAuth
   por Provider. Queda como decisión de producto.
2. **Correos de participantes**: `AssignmentSnapshot` no incluye correos; el evento se crea sin
   invitados y el acceso depende de la política del calendario.
3. **Invitación de Discord**: expiración hasta `accessEndsAt` y `max_uses` configurable (por defecto
   ilimitado durante la ventana) para permitir reentrada.
4. **Transacción durante llamada externa**: simplifica la atomicidad pero retiene una conexión
   hasta el timeout del proveedor. Si el volumen crece, se moverá a reserva + reconciliación.
5. **Base MNT-01**: el esquema mínimo de asignaciones se incluye aquí; debe reconciliarse con el
   equipo dueño de MNT-01 antes de fusionar.
6. **Spec preexistente**: `src/common/validators/is-future-date-time.validator.spec.ts` referencia un
   DTO de reservas ausente en la base; falla de forma independiente a esta HU.

## 6. Verificación

- `npm run build` en `BackendNest.js/` (compila NestJS ESM).
- `npm test`: pruebas unitarias, de adaptadores y HTTP (Vitest). El spec HTTP sustituye el
  `DataSource` por un doble para no requerir PostgreSQL.
- `npm run test:db`: pruebas de persistencia con Testcontainers PostgreSQL (requiere Docker):
  aplica las migraciones, valida la transacción atómica sala + outbox y el bloqueo asesor con 10
  solicitudes concurrentes.
- `npm run lint` (oxlint type-aware) y `npm run test:e2e`.
- No se ejecutan llamadas reales a Zoom, Google ni Discord.
- La migración se ejecuta únicamente contra el PostgreSQL efímero de Testcontainers, nunca contra
  una base compartida.
