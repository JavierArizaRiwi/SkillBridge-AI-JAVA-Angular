# Cancelación de reserva propia — Backend

## Historia de usuario

Como cliente autenticado, quiero cancelar una reserva propia que todavía pueda cancelarse, para liberar una sesión que ya no necesito.

## Alcance

Este trabajo cubre exclusivamente el backend Spring Boot. No incluye cambios en Angular, JavaScript, TypeScript, HTML, CSS, estados visuales, caché del frontend ni control visual de doble clic.

El compañero de frontend integrará el endpoint cuando el contrato quede publicado. El backend protegerá la operación contra solicitudes repetidas o concurrentes; el estado de carga del botón permanece fuera de alcance.

## Análisis inicial

| Componente | Archivo o ubicación | Situación actual | Cambio requerido |
|---|---|---|---|
| Plataforma | `backend/pom.xml` | Java 21, Spring Boot 3.5.6, Maven, JUnit 5, Mockito, Testcontainers y Flyway. | Reutilizar las herramientas existentes. |
| Arquitectura | `backend/src/main/java/com/riwi/skillbridge` | Hexagonal: dominio, puertos, servicios y adaptadores. | Mantener la separación por puertos y adaptadores. |
| Reserva | `domain/model/Booking.java` | `record` inmutable sin comportamiento. | Añadir transición de cancelación que devuelva una nueva reserva. |
| Estados | `domain/model/BookingStatus.java` | `CREATED`, `CONFIRMED`, `CANCELLED`, `COMPLETED`. | Permitir únicamente `CREATED -> CANCELLED`. |
| Entrada REST | `BookingController.java` | Crea reservas y lista las del usuario autenticado. | Añadir endpoint protegido de cancelación. |
| Identidad | `Authentication.getName()` y JWT | El subject JWT es el email del usuario. | Enviar el email autenticado explícitamente al caso de uso. |
| Persistencia | `BookingRepositoryPort` y `BookingPersistenceAdapter` | Guarda y lista por email; no busca por id. | Añadir consulta por id y actualización protegida. |
| Entidad | `BookingEntity.java` | Sin versión ni historial. | Incorporar control de concurrencia y auditoría. |
| Seguridad | `SecurityConfiguration` y `JwtAuthenticationFilter` | Rutas no públicas autenticadas; JWT inválido limpia el contexto. | Verificar y, si hace falta, asegurar respuesta 401. |
| Excepciones | `GlobalExceptionHandler.java` | 404 para recurso ausente y 422 para regla de negocio. | Mapear la cancelación según el contrato. |
| Eventos | RabbitMQ con `BookingCreatedEvent` | Publicación directa posterior a persistencia. | Añadir evento de cancelación una única vez. |
| Transacciones | Búsqueda global de backend | No hay `@Transactional`. | Declarar transacción de cancelación. |
| Concurrencia | Entidad y repositorios | No existe `@Version` ni bloqueo. | Proponer bloqueo optimista y prueba concurrente. |
| Auditoría | Código y migraciones actuales | No existe historial de reservas. | Crear almacenamiento de historial de cancelación. |
| Migraciones | `db/migration/V1__init.sql`, `V2__seed_data.sql` | Flyway activo. | Agregar migración nueva sin modificar las existentes. |

## Arquitectura encontrada

El backend usa arquitectura hexagonal. Los adaptadores REST dependen de puertos de entrada; los servicios de aplicación implementan esos puertos y usan puertos de salida; JPA y RabbitMQ son adaptadores de salida. El dominio no depende de Spring Security, JPA ni HTTP.

La reserva se persiste en PostgreSQL con JPA. La autenticación es stateless, mediante JWT cuyo subject contiene el email. RabbitMQ publica actualmente solo el evento de creación de reserva. Flyway administra el esquema.

## Decisiones técnicas confirmadas

| Decisión | Valor aprobado |
|---|---|
| Anticipación mínima | 24 horas antes de `scheduledAt`. |
| Reserva ajena | `404 Not Found`, para no revelar su existencia. |
| Segunda cancelación | Exitosa e idempotente; sin actualización, auditoría ni evento adicionales. |
| Endpoint recomendado | `PATCH /api/bookings/{bookingId}/cancel`. Ya está sugerido en `docs/API.md`. |
| Respuesta de cancelación repetida | `200 OK` con la reserva en `CANCELLED`, igual que la primera llamada exitosa. |
| Outbox transaccional | No se implementará por decisión aprobada. |

### Riesgo aceptado de publicación

La arquitectura actual persiste primero y publica RabbitMQ después. Sin outbox ni transacción distribuida, si RabbitMQ falla después del commit de PostgreSQL, la reserva puede quedar cancelada sin que el evento se publique. Este riesgo se mantiene por la decisión de no implementar outbox y se verificará que no haya duplicados en el flujo normal y concurrente.

## Contrato del endpoint propuesto

| Elemento | Contrato |
|---|---|
| Método y URL | `PATCH /api/bookings/{bookingId}/cancel` |
| Autorización | `Authorization: Bearer <JWT válido>` |
| Parámetro | `bookingId`: UUID de la reserva |
| Cuerpo | No requerido |
| Éxito inicial | `200 OK` con la reserva en `CANCELLED` |
| Éxito repetido | `200 OK` con la reserva ya `CANCELLED`; sin efectos nuevos |
| Reserva inexistente o ajena | `404 Not Found` |
| Regla de negocio | `422 Unprocessable Entity` |
| JWT ausente, expirado, alterado o malformado | `401 Unauthorized` |

Ejemplo de petición:

```http
PATCH /api/bookings/00000000-0000-0000-0000-000000000000/cancel
Authorization: Bearer <jwt>
```

Ejemplo de respuesta exitosa:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "offeringId": "00000000-0000-0000-0000-000000000000",
  "customerId": "00000000-0000-0000-0000-000000000000",
  "scheduledAt": "2030-10-10T15:00:00Z",
  "status": "CANCELLED"
}
```

## Flujo de cancelación

1. El adaptador REST obtiene el email desde `Authentication`.
2. Construye un comando con el id de reserva y la identidad autenticada.
3. El caso de uso busca la reserva y valida ownership sin revelar reservas ajenas.
4. Valida estado, fecha futura y anticipación mínima de 24 horas usando tiempo controlable.
5. El dominio ejecuta la transición efectiva de `CREATED` a `CANCELLED`.
6. La aplicación guarda la reserva y registra el historial dentro de una transacción.
7. Solo ante transición efectiva publica el evento de cancelación.
8. Una reserva ya cancelada retorna el estado actual sin nuevos efectos.

## Máquina de estados

| Estado inicial | Acción | Estado final | Resultado |
|---|---|---|---|
| `CREATED` | Cancelar dentro de las reglas temporales | `CANCELLED` | Transición efectiva. |
| `CANCELLED` | Cancelar de nuevo | `CANCELLED` | Éxito idempotente, sin efectos secundarios. |
| `CONFIRMED` | Cancelar | Sin cambio | Rechazo de regla de negocio. |
| `COMPLETED` | Cancelar | Sin cambio | Rechazo de regla de negocio. |

## Ownership, tiempo, JWT e idempotencia

La ownership se determina comparando la reserva con el usuario resuelto desde el email del JWT. Una reserva ajena se trata como no encontrada. La fecha debe ser futura y la diferencia entre el instante actual y `scheduledAt` debe ser de al menos 24 horas. El tiempo se abstraerá con `Clock` o la abstracción equivalente para no depender de la hora real en pruebas.

Las solicitudes sin JWT válido deben detenerse antes de ejecutar el caso de uso. Una segunda cancelación retorna la reserva ya cancelada y no debe persistir, auditar ni publicar de nuevo.

## Concurrencia, auditoría y eventos

Se propone bloqueo optimista con una versión en la entidad de persistencia. Dos solicitudes concurrentes podrán leer inicialmente `CREATED`, pero solo una debe completar la transición y producir historial y evento. La otra recibirá la respuesta idempotente controlada tras recargar el estado, o el mecanismo equivalente que se valide durante la implementación.

La auditoría persistirá un único registro para la transición real. Se creará un evento de dominio o aplicación `BookingCancelled` y se publicará una vez por transición efectiva. No se implementará outbox; el riesgo de fallo entre commit y RabbitMQ se mantiene documentado.

## Archivos y migraciones

| Tipo | Estado inicial |
|---|---|
| Archivos creados | Este documento. |
| Archivos modificados | Ninguno todavía. |
| Migraciones | Ninguna todavía; se prevé una nueva migración para auditoría y concurrencia. |

## Matriz de pruebas QA

| ID | Nivel de prueba | Clase de prueba | Preparación | Acción | Verificaciones |
|---|---|---|---|---|---|
| QA-01 | Dominio, caso de uso e integración | Pendiente | Propia, `CREATED`, futura y con >=24h | Cancelar | Estado, persistencia, historial, evento y consulta posterior. |
| QA-02 | Caso de uso e integración | Pendiente | JWT válido de otro usuario | Cancelar | 404; reserva, historial y evento intactos. |
| QA-03 | Caso de uso e integración | Pendiente | UUID inexistente | Cancelar | 404 y ausencia de efectos. |
| QA-04 | Dominio y caso de uso | Pendiente | Reserva propia con sesión pasada | Cancelar | 422 y ausencia de efectos. |
| QA-05 | Dominio y caso de uso | Pendiente | Reserva futura en los límites de 24h | Cancelar | Regla temporal, límites y ausencia de efectos. |
| QA-06 | Caso de uso e integración | Pendiente | Reserva cancelada por primera llamada | Cancelar de nuevo | 200, un historial y un evento. |
| QA-07 | Seguridad HTTP | Pendiente | JWT ausente, expirado, alterado o malformado | Llamar endpoint | 401 y caso de uso no ejecutado. |
| QA-08 | Persistencia y concurrencia | Pendiente | Dos ejecuciones sincronizadas | Cancelar simultáneamente | Una transición, historial y evento; datos íntegros. |

## Resultado de casos QA

| ID | Prueba automatizada | Estado | Evidencia | Observaciones |
|---|---|---|---|---|
| QA-01 | `BookingTest`, `BookingServiceTest` | Parcial | Transición propia válida, guardada a través del puerto simulado. | Faltan adaptador JPA, auditoría, evento e integración. |
| QA-02 | `BookingServiceTest` | Parcial | Reserva ajena devuelve error de no encontrada; no llama a `save` ni al publicador. | Falta integración HTTP. |
| QA-03 | `BookingServiceTest` | Parcial | UUID inexistente devuelve error de no encontrada sin llamar a usuario, `save` ni publicador. | Falta integración HTTP. |
| QA-04 | `BookingCancellationPolicyTest` | Parcial | Fechas pasada y actual rechazadas por la política temporal. | Falta integración HTTP. |
| QA-05 | `BookingCancellationPolicyTest`, `BookingServiceTest` | Parcial | Límite exacto de 24h permitido y 23:59:59 rechazado sin guardar ni publicar. | Falta integración HTTP. |
| QA-06 | `BookingTest`, `BookingServiceTest` | Parcial | Repetición retorna la reserva cancelada sin guardar ni publicar. | Faltan persistencia, auditoría, evento e integración. |
| QA-07 | Pendiente | No ejecutado | — | Se implementará en tareas posteriores. |
| QA-08 | Pendiente | No ejecutado | — | Se implementará en tareas posteriores. |

## Plan de tareas

1. Documentación inicial.
2. Comando y contrato de caso de uso.
3. Transición de dominio.
4. Reglas temporales.
5. Caso de uso, ownership y transacción.
6. Persistencia, auditoría y concurrencia.
7. Evento e idempotencia.
8. Endpoint y seguridad JWT.
9. Pruebas de integración y concurrencia.
10. Revisión final y trazabilidad completa.

## Comandos y resultados de pruebas

Tarea 0: no se ejecutaron pruebas porque no modificó comportamiento ejecutable.

Tarea 1: `mvn test -Dtest=CancelBookingCommandTest` ejecutado correctamente: 4 pruebas, 0 fallos, 0 errores y 0 omitidas.

Tarea 2: `mvn test -Dtest=BookingTest` ejecutado correctamente: 4 pruebas, 0 fallos, 0 errores y 0 omitidas.

Tarea 3: `mvn test -Dtest=BookingCancellationPolicyTest` ejecutado correctamente: 5 pruebas, 0 fallos, 0 errores y 0 omitidas.

Tarea 4: `mvn test -Dtest=BookingServiceTest` ejecutado correctamente: 6 pruebas, 0 fallos, 0 errores y 0 omitidas. La ejecución requiere una JVM con permiso para adjuntar el agente de Mockito.

## Historial de cambios y commits

| Tarea | Archivo | Cambio | Razón | Caso QA | Prueba | Commit |
|---|---|---|---|---|---|---|
| Tarea 0 | `docs/cancelacion-reserva-propia-backend.md` | Análisis, decisiones, contrato y matriz inicial. | Trazabilidad previa a implementación. | QA-01 a QA-08 | No aplica aún. | `ac7e56f` |
| Tarea 1 | `application/port/in/CancelBookingCommand.java` | Comando inmutable con id de reserva y email autenticado. | Evitar acoplamiento de la aplicación con Spring Security y HTTP. | QA-01, QA-02, QA-03, QA-06 | `CancelBookingCommandTest` | `d8ca969` |
| Tarea 1 | `application/port/in/CancelBookingUseCase.java` | Puerto de entrada de cancelación. | Establecer el contrato del caso de uso antes de su implementación. | QA-01 a QA-06 | Compilación y `CancelBookingCommandTest`. | `d8ca969` |
| Tarea 2 | `domain/model/Booking.java` | Transición inmutable de `CREATED` a `CANCELLED` e idempotencia de `CANCELLED`. | Mantener las reglas de estado dentro del dominio. | QA-01, QA-06 | `BookingTest` | `ec3192b` |
| Tarea 2 | `domain/model/BookingTest.java` | Pruebas de transición válida, repetida y estados inválidos. | Evitar regresiones de la máquina de estados. | QA-01, QA-06 | `BookingTest` | `ec3192b` |
| Tarea 3 | `domain/service/BookingCancellationPolicy.java` | Política temporal con reloj inyectable y anticipación mínima configurable. | Mantener las reglas temporales fuera del controlador y probarlas de forma determinista. | QA-04, QA-05 | `BookingCancellationPolicyTest` | `b06d1db` |
| Tarea 3 | `infrastructure/config/BookingCancellationConfiguration.java` | Configura reloj UTC y política de 24 horas. | Centralizar parámetros de aplicación sin acoplar el dominio a Spring. | QA-04, QA-05 | Prueba de política con `Clock.fixed`. | `b06d1db` |
| Tarea 4 | `application/port/out/BookingRepositoryPort.java` | Consulta de reserva por UUID. | Permitir que el caso de uso identifique la reserva objetivo. | QA-01, QA-02, QA-03, QA-06 | `BookingServiceTest` | Pendiente de aprobación. |
| Tarea 4 | `infrastructure/adapter/out/persistence/BookingPersistenceAdapter.java` | Adaptador de consulta por UUID. | Implementar el nuevo puerto sobre JPA. | QA-01, QA-02, QA-03, QA-06 | Compilación; prueba de integración pendiente. | Pendiente de aprobación. |
| Tarea 4 | `application/service/BookingService.java` | Caso de uso transaccional con ownership, política temporal e idempotencia. | Coordinar el flujo de cancelación sin acoplarlo a HTTP o Spring Security. | QA-01 a QA-06 | `BookingServiceTest` | Pendiente de aprobación. |
| Tarea 4 | `application/service/BookingServiceTest.java` | Casos de uso válidos, ajenos, inexistentes, temporales e idempotentes. | Verificar resultado y ausencia de persistencia o eventos en rechazos. | QA-01 a QA-06 | `BookingServiceTest` | Pendiente de aprobación. |

## Instrucciones de integración para frontend

Cuando el endpoint esté implementado, el frontend deberá enviar `PATCH` a `/api/bookings/{bookingId}/cancel` con el JWT ya existente. No debe enviar el id del usuario ni cuerpo de solicitud. Debe tratar `200` como cancelación aplicada o ya aplicada; `404` como reserva no disponible; `422` como regla temporal o de estado; y `401` como sesión inválida. Los estados visuales y la prevención visual de doble clic son responsabilidad del frontend.

## Decisiones pendientes

No hay decisiones funcionales pendientes. Queda validar técnicamente la forma exacta de resolver el conflicto de bloqueo optimista para que la segunda solicitud concurrente conserve el contrato idempotente.
