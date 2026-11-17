# ADR-0006: Arquitectura híbrida — monolito modular + servicios satélite

## Status

Proposed (requiere aprobación humana)

## Date

2026-11-09

## Context

SkillBridge AI nació como un monolito modular hexagonal (`backend/`, ADR-0001) con un microservicio
de auditoría (`audit-service/`). Se incorporan ahora:

- Un servicio de **mentorías en NestJS + Prisma** (issues #83–#91, HU-MNT-01..09), a cargo de otro equipo.
  Hoy las mentorías viven dentro del monolito (`MentorshipController`, migraciones V8–V10) y se migrarán.
- Un **job batch Spark** (HU-25, #76) y funciones de IA (RAG HU-26, fallback HU-27/28).

Sin una regla explícita sobre qué vive dónde y cómo se comunican, cada equipo decide por su cuenta
y los contratos divergen (ver `docs/contracts/mentorships/gap-matrix.md`).

## Decision

### 1. Reparto de responsabilidades

| Componente | Tipo | Dueño de | No debe |
|---|---|---|---|
| `backend/` (Spring) | Monolito modular hexagonal | Identidad, catálogo, reservas, pagos, IA, notificaciones | Conocer el modelo interno de los servicios satélite |
| `audit-service/` (Spring) | Microservicio | Historial de auditoría (MongoDB) | Escribir en la BD del monolito |
| `mentorship-service/` (NestJS) | Microservicio | Asignaciones, recursos, progreso, salas en vivo (PostgreSQL propio) | Llamar HTTP síncrono al monolito durante el aprovisionamiento |
| Job Spark (HU-25) | Job batch, **no servicio** | Cálculo histórico de métricas | Estar en el camino transaccional |

### 2. Reglas de comunicación

1. **Una BD por dueño.** Nadie lee tablas de otro componente.
2. **Hechos punto a punto → RabbitMQ** (exchange `payment.events`, topic). El productor publica
   el estado completo necesario (Event-Carried State Transfer); el consumidor no vuelve a consultar.
3. **Log de eventos de negocio → Kafka** (topic `booking-events`, ADR-0003) con el envelope
   `BusinessEvent`. Lo consumen audit, analytics y Spark.
4. **HTTP síncrono solo cliente → servicio**, enrutado por prefijo en el borde. Sin HTTP entre
   servicios en el flujo de pago.
5. **Cada evento y cada API versionados** (`/api/v1/...`, campo `version` del envelope).
6. **Identidad:** el monolito es el único emisor de JWT y firma con **RS256**. Los servicios solo
   validan con la clave pública (`JWT_PUBLIC_KEY`). Ningún servicio posee la clave privada.

### 3. Mentorías: transición

- El código de mentorías del monolito es **deuda aceptada y temporal**: `MentorshipController` usa
  repositorios JPA directamente (viola la regla hexagonal de `AGENTS.md` §1). **No se refactoriza**;
  se elimina cuando el servicio Nest esté en producción.
- Entretanto el monolito **no recibe features nuevas de mentorías**; solo el cambio aditivo para
  publicar `PaymentApproved` enriquecido.
- Todo defecto del código de mentorías del monolito se registra como issue enlazado a #68, y no se
  corrige si el corte está próximo.

### 4. Envelope de eventos

`BusinessEvent` (`application/port/out/event/`) **no importa Spring** (verificado), pero sí Jackson
(anotaciones y un `ObjectMapper` creado en cada llamada a `getPayloadForAudit`). El contrato
vinculante es el **JSON** descrito en `docs/contracts/mentorships/events.md`, no la clase Java.
Mejora pendiente, no bloqueante: sacar la serialización del record a un adaptador.

### 5. Datos de oferta para mentorías

`Offering` incorpora `mode` (`ASYNCHRONOUS` | `LIVE`) y `durationHours`, para que
`PaymentApproved` v2 transporte el estado completo sin consultas posteriores.

### 6. Auditoría

`audit-service` debe aceptar los eventos de mentoría (`aggregateType = Mentorship`) y tolerar tipos
de evento desconocidos sin descartarlos ni bloquear el consumidor.

## Consequences

- El equipo Nest puede construir contra contratos escritos sin leer el monolito.
- Migrar a RS256 obliga a rotar el secreto actual y a generar un par de claves por entorno
  (ver `docs/contracts/mentorships/security.md`).
- El monolito debe enriquecer `PaymentApproved` y migrar `Offering` (nueva migración Flyway).
- Spark queda como job, no como servicio: menos superficie operativa.
- Se necesita enrutamiento por prefijo en el borde. **[UNKNOWN]** no verificado si
  `frontend/nginx.conf.template` hace proxy de API.
