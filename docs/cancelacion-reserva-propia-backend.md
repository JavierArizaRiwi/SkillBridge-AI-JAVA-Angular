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

### Corrección incorporada desde el PR

Después de integrar la rama en `develop`, el proyecto sustituyó el anterior
`BookingEventPublisherPort` por `NotificationPublisherPort`. La corrección evita
un error de inyección de beans en `BookingService`. Las siguientes tareas usarán
`NotificationMessage` y `NotificationType`; no se reintroducirá el puerto
anterior.

## Contrato del endpoint implementado

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

La entidad conserva versión optimista como protección adicional. Para la cancelación se usa un bloqueo pesimista de escritura al leer la reserva dentro de la transacción: la primera solicitud realiza la transición y la segunda espera, vuelve a leer `CANCELLED` y retorna la respuesta idempotente sin guardar historial ni publicar otro evento. La prueba de concurrencia usa dos solicitudes sincronizadas con `CyclicBarrier`, sin depender de `Thread.sleep`.

La auditoría persistirá un único registro para la transición real. Se creará un evento de dominio o aplicación `BookingCancelled` y se publicará una vez por transición efectiva. No se implementará outbox; el riesgo de fallo entre commit y RabbitMQ se mantiene documentado.

La implementación usa `NotificationMessage` con tipo `BOOKING_CANCELLED` y routing key `notification.booking.cancelled`, siguiendo la corrección integrada desde el PR. La misma cola de notificaciones consume creaciones y cancelaciones según su tipo.

## Archivos y migraciones

| Tipo | Estado final |
|---|---|
| Archivos creados | `CancelBookingCommand`, `CancelBookingUseCase`, `BookingStatusHistory`, adaptador de historial, migración V4, pruebas de dominio, servicio, HTTP e integración. |
| Archivos modificados | Dominio `Booking`, caso de uso, puertos, adaptadores JPA/RabbitMQ, controlador, seguridad de prueba y documentación API. |
| Migraciones | `V6__booking_cancellation_audit.sql`: versión optimista e historial de estados de reserva. |

## Matriz de pruebas QA

| ID | Nivel de prueba | Clase de prueba | Preparación | Acción | Verificaciones |
|---|---|---|---|---|---|
| QA-01 | Integración HTTP, BD y RabbitMQ | `BookingCancellationIT` | Propia, `CREATED`, futura y con >=24h | Cancelar | `200`, estado, historial y una notificación. |
| QA-02 | Integración HTTP, BD y RabbitMQ | `BookingCancellationIT` | JWT válido de otro usuario | Cancelar | `404`; reserva, historial y evento intactos. |
| QA-03 | Integración HTTP | `BookingCancellationIT` | UUID inexistente | Cancelar | `404` y ausencia de efectos. |
| QA-04 | Integración HTTP y BD | `BookingCancellationIT` | Reserva propia con sesión pasada | Cancelar | `422` y ausencia de efectos. |
| QA-05 | Integración HTTP y BD | `BookingCancellationIT` | Reserva futura en los límites de 24h | Cancelar | Regla temporal, límite y ausencia de efectos. |
| QA-06 | Integración HTTP, BD y RabbitMQ | `BookingCancellationIT` | Reserva cancelada por primera llamada | Cancelar de nuevo | `200`, un historial y un evento. |
| QA-07 | Integración de seguridad | `BookingCancellationIT` | JWT ausente, expirado, alterado y encabezado inválido | Llamar endpoint | `401` y caso de uso no ejecutado. |
| QA-08 | Integración HTTP, persistencia y concurrencia | `BookingCancellationIT` | Dos solicitudes sincronizadas con `CyclicBarrier` | Cancelar simultáneamente | Dos respuestas controladas, una transición, un historial y un evento. |

## Resultado de casos QA

| ID | Prueba automatizada | Estado | Evidencia | Observaciones |
|---|---|---|---|---|
| QA-01 | `BookingTest`, `BookingServiceTest`, `BookingCancellationIT` | Parcial | Transición propia válida, historial y una notificación `BOOKING_CANCELLED` verificados mediante puertos simulados. | La prueba integral compila, pero Testcontainers no pudo iniciar por incompatibilidad local de API Docker. |
| QA-02 | `BookingServiceTest`, `BookingCancellationIT` | Parcial | Reserva ajena devuelve no encontrada; no guarda, audita ni publica notificación. | Integración pendiente de ejecutar en Docker compatible. |
| QA-03 | `BookingServiceTest`, `BookingCancellationIT` | Parcial | UUID inexistente no accede a usuario, no guarda, audita ni publica notificación. | Integración pendiente de ejecutar en Docker compatible. |
| QA-04 | `BookingCancellationPolicyTest`, `BookingCancellationIT` | Parcial | Fechas pasada y actual rechazadas por la política temporal. | Integración pendiente de ejecutar en Docker compatible. |
| QA-05 | `BookingCancellationPolicyTest`, `BookingServiceTest`, `BookingCancellationIT` | Parcial | Límite exacto de 24h permitido y 23:59:59 rechazado sin guardar ni publicar. | Integración pendiente de ejecutar en Docker compatible. |
| QA-06 | `BookingTest`, `BookingServiceTest`, `BookingCancellationIT` | Parcial | Repetición retorna la reserva cancelada sin guardar, auditar ni publicar una segunda notificación. | Integración pendiente de ejecutar en Docker compatible. |
| QA-07 | `BookingControllerSecurityTest`, `BookingCancellationIT` | Parcial | JWT ausente o alterado retorna `401` y no invoca el caso de uso. | La prueba integral incorpora JWT real expirado y encabezado malformado, pendiente de Docker compatible. |
| QA-08 | `BookingCancellationIT` | Parcial | Dos solicitudes MockMvc sincronizadas; se verifica `200`, estado final, un historial y una notificación. | Compila; ejecución pendiente en Docker compatible. |

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

Tarea 5: `mvn test -Dtest=BookingServiceTest,BookingTest,BookingCancellationPolicyTest` ejecutado correctamente: 18 pruebas, 0 fallos, 0 errores y 0 omitidas. `BookingPersistenceAdapterTest` no pudo ejecutarse porque la configuración local de Testcontainers negocia una API Docker 1.32, inferior a la mínima 1.40 de Docker Desktop; la prueba quedó creada y pendiente de ejecutarse en un entorno compatible.

Tarea 6: `mvn test -Dtest=BookingServiceTest,NotificationMessageTest,RabbitNotificationPublisherTest` ejecutado correctamente: 16 pruebas, 0 fallos, 0 errores y 0 omitidas. La prueba de fallo de RabbitMQ registra el error esperado sin propagarlo.

Tarea 7: `mvn test -Dtest=BookingControllerSecurityTest` ejecutado correctamente: 6 pruebas, 0 fallos, 0 errores y 0 omitidas. Cubre el contrato `PATCH`, la identidad tomada del contexto de seguridad, las respuestas `404` y `422`, la repetición idempotente y JWT ausente o alterado.

Tarea 8: `mvn test -Dtest=BookingCancellationIT` compiló las 19 clases de prueba correctamente, pero no ejecutó los casos QA porque Testcontainers no pudo iniciar Ryuk. Docker Desktop exige un cliente mínimo API 1.40 y la configuración local de Testcontainers negocia API 1.32. Resultado: 1 error de infraestructura, 0 fallos de aserción. No se modificó la configuración global del equipo.

Tarea 9: `mvn test -Dtest=BookingServiceTest` ejecutado correctamente: 9 pruebas, 0 fallos, 0 errores y 0 omitidas. `mvn test -Dtest=BookingCancellationIT` compiló correctamente las 90 clases de producción y las 19 clases de prueba tras incorporar el bloqueo pesimista y QA-08; Testcontainers volvió a fallar antes de ejecutar aserciones por la misma negociación de API Docker 1.32 frente al mínimo 1.40. Resultado: 1 error de infraestructura, 0 fallos de aserción.

Tarea 10: `mvn test` ejecutó 74 pruebas: 72 finalizaron correctamente, 0 fallaron por aserciones y 2 quedaron bloqueadas antes de iniciar por Testcontainers (`JpaOfferingRepositoryTest` y `BookingPersistenceAdapterTest`). La causa es la negociación de API Docker 1.32 frente al mínimo 1.40 de Docker Desktop. No hay herramientas de análisis estático o formateo configuradas en `pom.xml`. `git diff --check` terminó sin errores.

Corrección posterior: `BookingCancellationIT` excluye la autoconfiguración de Google GenAI y simula `AiRecommendationPort`, evitando que la prueba de cancelación dependa de credenciales Gemini. `mvn test -Dtest=BookingCancellationIT` compiló correctamente, pero Testcontainers volvió a bloquear la ejecución antes de crear el contexto Spring.

Corrección posterior: `BookingCancellationIT` simula `BookingEventPublisherPort` y desactiva el listener Kafka únicamente para esta prueba. La integración conserva PostgreSQL y RabbitMQ reales con Testcontainers, mientras verifica que el evento de negocio se publica una sola vez en las cancelaciones válidas e idempotentes. Así no intenta conectarse a `localhost:9092` ni depende del topic `booking-events`. `mvn test -Dtest=BookingCancellationIT` volvió a detenerse antes del contexto por falta de acceso local a Docker; no hubo fallos de aserción.

Corrección posterior: al integrar la seguridad por cookies de `develop`, `BookingControllerSecurityTest` simula `AuthCookieService` y agrega el token CSRF a los `PATCH` que deben atravesar la protección CSRF. Con ello, la prueba sigue cubriendo el controlador de cancelación bajo la cadena de seguridad actual sin depender de una cookie real. `mvn -q -Dtest=BookingControllerSecurityTest test` terminó correctamente: 6 pruebas, 0 fallos y 0 errores.

Corrección posterior: CSRF se exige únicamente en solicitudes mutables que ya fueron autenticadas mediante cookie. `JwtAuthenticationFilter` se ejecuta antes de CSRF para distinguir esa autenticación; Bearer, JWT ausente, expirado o alterado no requieren CSRF y llegan al manejador `401`. `mvn -q -Dtest=BookingControllerSecurityTest,OfferingSecurityTest test` terminó correctamente: 23 pruebas, 0 fallos y 0 errores. La ejecución de `BookingCancellationIT` sigue pendiente por la API Docker local 1.32, inferior al mínimo 1.40 del daemon.

Corrección posterior: Testcontainers se actualizó de `1.21.3` a `1.21.4`, que negocia correctamente la API Docker de Docker Desktop 29. Las pruebas de persistencia y `BookingCancellationIT` iniciaron PostgreSQL, RabbitMQ y Ryuk correctamente con esta versión. `mvn -q test` terminó correctamente: 84 pruebas, 0 fallos, 0 errores y 0 omitidas.

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
| Tarea 4 | `application/port/out/BookingRepositoryPort.java` | Consulta de reserva por UUID. | Permitir que el caso de uso identifique la reserva objetivo. | QA-01, QA-02, QA-03, QA-06 | `BookingServiceTest` | `fbb5ad0` |
| Tarea 4 | `infrastructure/adapter/out/persistence/BookingPersistenceAdapter.java` | Adaptador de consulta por UUID. | Implementar el nuevo puerto sobre JPA. | QA-01, QA-02, QA-03, QA-06 | Compilación; prueba de integración pendiente. | `fbb5ad0` |
| Tarea 4 | `application/service/BookingService.java` | Caso de uso transaccional con ownership, política temporal e idempotencia. | Coordinar el flujo de cancelación sin acoplarlo a HTTP o Spring Security. | QA-01 a QA-06 | `BookingServiceTest` | `fbb5ad0` |
| Tarea 4 | `application/service/BookingServiceTest.java` | Casos de uso válidos, ajenos, inexistentes, temporales e idempotentes. | Verificar resultado y ausencia de persistencia o eventos en rechazos. | QA-01 a QA-06 | `BookingServiceTest` | `fbb5ad0` |
| Tarea 5 | `db/migration/V4__booking_cancellation_audit.sql` | Versión optimista e historial de transiciones. | Evitar actualizaciones concurrentes silenciosas y conservar auditoría. | QA-01, QA-06, QA-08 | `BookingPersistenceAdapterTest` pendiente por entorno Docker. | `284ae46` |
| Tarea 5 | `domain/model/Booking.java` y `BookingStatusHistory.java` | Versión del agregado y registro inmutable de cambio de estado. | Propagar control de concurrencia y representar la auditoría en el dominio. | QA-01, QA-06, QA-08 | Pruebas unitarias de dominio y servicio. | `284ae46` |
| Tarea 5 | `application/port/out/BookingStatusHistoryPort.java` y adaptador JPA | Puerto y persistencia del historial. | Mantener la aplicación independiente de JPA. | QA-01, QA-06, QA-08 | `BookingServiceTest`; integración pendiente por entorno Docker. | `284ae46` |
| Tarea 5 | `application/service/BookingService.java` y `BookingServiceTest.java` | Guarda una auditoría única tras una transición efectiva y verifica ausencia de efectos en rechazos. | Mantener reserva e historial dentro de la transacción. | QA-01 a QA-06 | 18 pruebas unitarias seleccionadas. | `284ae46` |
| Tarea 6 | `NotificationType`, `NotificationMessage`, `RabbitConfiguration` y `RabbitNotificationPublisher` | Tipo, mensaje y routing key de cancelación. | Publicar la cancelación mediante la infraestructura de notificaciones corregida. | QA-01, QA-06, QA-08 | Pruebas de mensaje y publicador Rabbit. | `47588e0` |
| Tarea 6 | `BookingService.java` y `BookingServiceTest.java` | Publica una sola notificación tras la transición y auditoría efectivas. | Evitar publicaciones en rechazos o llamadas repetidas. | QA-01, QA-02, QA-03, QA-05, QA-06 | `BookingServiceTest`. | `47588e0` |
| Tarea 7 | `BookingController.java` | Endpoint `PATCH /api/bookings/{bookingId}/cancel` que toma el email de `Authentication`. | Exponer la cancelación sin recibir identidad del cliente. | QA-01, QA-02, QA-03, QA-05, QA-06 | `BookingControllerSecurityTest`. | `48a231e` |
| Tarea 7 | `BookingControllerSecurityTest.java` y `docs/API.md` | Pruebas MockMvc de contrato y seguridad; endpoint publicado en la referencia de API. | Validar respuestas HTTP y facilitar la integración del frontend. | QA-01, QA-02, QA-03, QA-05, QA-06, QA-07 | `BookingControllerSecurityTest`. | `48a231e` |
| Tarea 8 | `BookingCancellationIT.java` | Prueba integral con MockMvc, JWT real, reloj fijo, PostgreSQL y RabbitMQ mediante Testcontainers. | Cubrir QA-01 a QA-07 contra adaptadores reales. | QA-01 a QA-07 | Compila; ejecución bloqueada por incompatibilidad local Docker/Testcontainers. | `7f64a83` |
| Tarea 9 | `BookingRepositoryPort.java`, `BookingPersistenceAdapter.java` y `JpaBookingRepository.java` | Lectura bloqueada con `PESSIMISTIC_WRITE` para la cancelación. | Serializar cancelaciones de una misma reserva y mantener el segundo resultado idempotente. | QA-08 | `BookingServiceTest` aprobado; integración pendiente por Docker. | `4109cff` |
| Tarea 9 | `BookingService.java`, `BookingServiceTest.java` y `BookingCancellationIT.java` | El caso de uso usa la lectura bloqueada, se ajustan sus stubs y la prueba sincroniza dos `PATCH`. | Comprobar una sola transición, historial y notificación sin `Thread.sleep`. | QA-08 | 9 pruebas unitarias aprobadas; integración pendiente por Docker. | `4109cff` |
| Tarea 10 | `docs/cancelacion-reserva-propia-backend.md` | Revisión final de criterios, QA, pruebas, alcance y riesgos. | Entregar evidencia verificable al equipo. | QA-01 a QA-08 | Suite completa intentada; pruebas con Testcontainers bloqueadas por Docker local. | Pendiente de aprobación. |
| Corrección posterior | `BookingCancellationIT.java` | Excluye Google GenAI y simula el puerto de recomendaciones. | Evitar dependencia de credenciales Gemini en la prueba de integración. | QA-01 a QA-08 | Compila; Testcontainers bloquea el arranque antes del contexto Spring. | Pendiente de aprobación. |
| Corrección posterior | `BookingCancellationIT.java` | Simula el puerto de eventos Kafka y desactiva su listener para la prueba; verifica una sola publicación o ausencia de ella según el caso. | Evitar la dependencia de Kafka externo y conservar la evidencia de eventos del flujo de cancelación. | QA-01 a QA-08 | Ejecución intentada; Testcontainers no puede acceder a Docker local. | Pendiente de aprobación. |
| Corrección posterior | `BookingControllerSecurityTest.java` | Simula `AuthCookieService` y adjunta CSRF a los `PATCH` que lo requieren. | Mantener las pruebas HTTP de cancelación compatibles con la autenticación por cookies integrada desde `develop`. | QA-01 a QA-07 | `BookingControllerSecurityTest`: 6 pruebas aprobadas. | Pendiente de aprobación. |
| Corrección posterior | `SecurityConfiguration.java` y `BookingControllerSecurityTest.java` | Exige CSRF solo con sesión por cookie autenticada y conserva `401` ante JWT ausente o inválido. | Evitar que CSRF reemplace indebidamente las respuestas de autenticación de la API. | QA-07 | `BookingControllerSecurityTest` y `OfferingSecurityTest`: 23 pruebas aprobadas. | Pendiente de aprobación. |
| Corrección posterior | `backend/pom.xml` | Actualiza Testcontainers a `1.21.4`. | Compatibilidad con la API mínima 1.40 de Docker Desktop 29. | QA-01 a QA-08 | Suite completa: 84 pruebas aprobadas. | Pendiente de aprobación. |

## Instrucciones de integración para frontend

Cuando el endpoint esté implementado, el frontend deberá enviar `PATCH` a `/api/bookings/{bookingId}/cancel` con el JWT ya existente. No debe enviar el id del usuario ni cuerpo de solicitud. Debe tratar `200` como cancelación aplicada o ya aplicada; `404` como reserva no disponible; `422` como regla temporal o de estado; y `401` como sesión inválida. Los estados visuales y la prevención visual de doble clic son responsabilidad del frontend.

## Decisiones pendientes

No hay decisiones funcionales pendientes. Falta ejecutar las pruebas de integración y concurrencia en un entorno Docker compatible con Testcontainers.

## Revisión final

| Criterio de aceptación | Estado | Evidencia |
|---|---|---|
| Endpoint protegido y usuario desde JWT | Parcial | `BookingControllerSecurityTest` aprobado; JWT real queda en `BookingCancellationIT`, pendiente de Docker. |
| Ownership y ocultación de reserva ajena | Parcial | `BookingServiceTest` y prueba HTTP de controlador aprobados; integración pendiente. |
| Estado, fecha futura y anticipación de 24 horas | Parcial | `BookingTest` y `BookingCancellationPolicyTest` aprobados; integración pendiente. |
| Cancelación cambia a `CANCELLED` y conserva historial | Parcial | Servicio y adaptadores implementados; validación JPA pendiente de Testcontainers. |
| Idempotencia sin efectos duplicados | Parcial | Pruebas unitarias aprobadas; integración pendiente. |
| Una notificación por transición efectiva | Parcial | Pruebas unitarias de publicador y servicio aprobadas; flujo RabbitMQ integral pendiente. |
| Transacción y concurrencia | Parcial | `@Transactional`, versión optimista y `PESSIMISTIC_WRITE` implementados; QA-08 pendiente de Testcontainers. |
| Alcance exclusivo de backend | Aprobado | No hay archivos de frontend en los commits de esta historia. |

### Riesgos pendientes

1. Testcontainers no puede crear contenedores con la negociación local de API Docker; deben ejecutarse `mvn test` y `mvn verify` en un entorno compatible para cerrar QA-01 a QA-08.
2. Por decisión aprobada, no existe outbox: un fallo de RabbitMQ después de persistir puede dejar una cancelación sin notificación.
