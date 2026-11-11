# Mentorías — máquina de estados

Estado de la **asignación** (`MentorshipStatus`). No sustituye al estado del pago ni al de la reserva
(`BookingStatus`: CREATED, CONFIRMED, CANCELLED, COMPLETED).

Fuente de verdad: el servicio de mentorías (NestJS). El enum del monolito
(`domain/model/MentorshipStatus.java`) coincide y se congela hasta el corte.

## Estados

| Estado | Significado | Acceso al aula/sala | Terminal |
|---|---|---|---|
| `PENDING_PAYMENT` | Sin pago aprobado | Bloqueado | No |
| `SCHEDULED` | Pagada y con ventana asignada | Solo dentro de `[accessStartsAt, accessEndsAt)` | No |
| `IN_PROGRESS` | Iniciada | Dentro de la ventana | No |
| `COMPLETED` | Entregada; **pago consumido** | Bloqueado | Sí |
| `CANCELLED` | Asignación cancelada | Bloqueado | Sí |
| `EXPIRED` | Ventana finalizada sin completar (modo `ASYNCHRONOUS`) | Bloqueado | Sí |
| `NO_SHOW` | Sesión en vivo finalizada sin asistencia (modo `LIVE`) | Bloqueado | Sí |

## Transiciones válidas

| Desde | Hacia | Disparador | Actor | Condición |
|---|---|---|---|---|
| `PENDING_PAYMENT` | `SCHEDULED` | Evento `PaymentApproved` | Sistema | Idempotente por `bookingId` |
| `PENDING_PAYMENT` | `CANCELLED` | Reserva cancelada antes del pago | Sistema | — |
| `SCHEDULED` | `IN_PROGRESS` | Primer progreso `> 0`, o ingreso a sala `LIVE` | Customer / Sistema | Dentro de la ventana |
| `SCHEDULED` | `COMPLETED` | Progreso `= 100` | Customer | Dentro de la ventana; se registran dos transiciones (→`IN_PROGRESS`→`COMPLETED`) |
| `SCHEDULED` | `CANCELLED` | Cancelación | Provider dueño / Admin / Sistema (reserva cancelada) | Motivo obligatorio |
| `SCHEDULED` | `EXPIRED` | Fin de ventana | Sistema | Solo `ASYNCHRONOUS` |
| `SCHEDULED` | `NO_SHOW` | Fin de ventana sin asistencia | Provider dueño / Sistema | Solo `LIVE` |
| `IN_PROGRESS` | `COMPLETED` | Progreso `= 100`, o cierre del mentor | Customer / Provider dueño | Dentro de la ventana |
| `IN_PROGRESS` | `EXPIRED` | Fin de ventana sin completar | Sistema | Solo `ASYNCHRONOUS` |
| `IN_PROGRESS` | `CANCELLED` | Cancelación excepcional | Admin | Motivo obligatorio |

Cualquier otra transición se rechaza con `409 Conflict` (código `INVALID_STATE_TRANSITION`).
**Los estados terminales no tienen salida**: nada reactiva un pago consumido.

## Invariantes

1. **1 pago = 1 mentoría.** `booking_id` es único; un `PaymentApproved` repetido no crea otra fila.
2. `COMPLETED` es irreversible: reiniciar progreso devuelve `409`.
3. El progreso es monótono: un valor menor al actual se rechaza (`422`). Repetir el mismo valor es idempotente.
4. `progressPercent` solo se acepta dentro de la ventana de acceso; fuera de ella, `403`.
5. Completar una asignación no altera las de otros participantes.
6. **Reprogramación autorizada:** mantiene la misma asignación en `SCHEDULED`, reemplaza la ventana y
   emite `MentorshipRescheduled`. No crea derechos duplicados. Solo desde `SCHEDULED`.
7. Cada transición registra: actor, fecha, estado anterior, estado nuevo y motivo (tabla de historial).
8. Un Provider solo opera asignaciones de sus ofertas; Admin opera todas; el Customer solo avanza
   su propio progreso y nunca aprueba pagos ni reabre asignaciones.

## Decisiones abiertas (requieren Product Owner, ver #68)

- **[DECIDIR]** Políticas de cancelación y reembolso: no definen aquí ninguna reactivación de pago.
- **[DECIDIR]** Política de ausencia en `LIVE`: ¿tolerancia de minutos antes de `NO_SHOW`?
- **[DECIDIR]** ¿Una asignación `EXPIRED` puede reprogramarse por excepción de Admin? Por defecto **no**.

## Diferencias con el monolito actual

- El monolito permite saltar de cualquier estado no terminal a `COMPLETED`/`IN_PROGRESS` sin historial
  y acepta bajar el progreso. Estas reglas son más estrictas; el servicio Nest es el que manda.
- El monolito no tiene transición automática a `EXPIRED`/`NO_SHOW` (no hay job).
