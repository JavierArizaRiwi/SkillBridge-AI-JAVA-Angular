# Reporte de verificación — HU-MNT-07 (Salas de mentoría en vivo multi-proveedor)

**Feature**: `specs/089-mentorship-live-multi-provider/`
**Destino**: `BackendNest.js/` (NestJS 12, ESM, TypeORM, Vitest)
**Fecha**: 2026-11-16

## Alcance verificado

Implementación completa de FR-001…FR-021 y NFR-001…NFR-004 sobre `BackendNest.js`:
endpoints `POST /api/v1/mentorships/:id/live-room` y `GET /api/v1/mentorships/:id/access`,
proveedores Zoom/Google Meet/Discord, idempotencia, bloqueo asesor, outbox atómico,
cifrado AES-256-GCM, contrato de errores `{ code, message, correlationId }` y métricas por
proveedor.

## Resultados (ejecutados en esta sesión)

| Comando | Resultado |
|---|---|
| `npm run build` | OK |
| `npm run lint` (oxlint type-aware) | OK |
| `npx vitest run test/` | 44 passed, 3 skipped |
| `npx vitest run --config vitest.config.e2e.ts` | 1 passed |

## No ejecutado

- `npm run test:db` (persistencia con Testcontainers PostgreSQL): **no ejecutado en esta
  sesión**, Docker daemon no disponible. Los 3 tests de persistencia quedan skipped.

## Fallo pre-existente (no relacionado con esta HU)

**Nombre del hallazgo**: *Spec huérfano `is-future-date-time.validator.spec.ts`*.

- **Archivo**: `BackendNest.js/src/common/validators/is-future-date-time.validator.spec.ts:2`.
- **Síntoma**: `Cannot find module '../../modules/reservations/dto/availability-query.dto.js'`.
- **Causa**: el spec referencia un DTO del dominio de reservas que no existe en
  `BackendNest.js` (no hay `src/modules`).
- **Origen**: commit base `0e5477f feat: integrar backend NestJS para celula 3`; ajeno a MNT-07.
- **Impacto**: `npm test` (que incluye `src/**/*.spec.ts`) termina en rojo por este archivo.
- **Decisión**: reportar sin modificar (fuera del alcance de la HU).
