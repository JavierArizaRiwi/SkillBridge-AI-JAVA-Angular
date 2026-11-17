# Feature Specification: Salas de mentoría en vivo multi-proveedor (HU-MNT-07)

**Feature Branch**: `feature/089-mentorship-live-multi-provider`
**Created**: 2026-11-10
**Status**: Draft (propuesta; pendiente de aprobación humana)
**Issue**: [#89](https://github.com/Danilo-Doria/SkillBridge-AI-JAVA-Angular/issues/89)
**Input**: Como Provider de una mentoría sincrónica, quiero generar la sala de reunión seleccionando entre Zoom, Google Meet o Discord, para impartir la sesión en la plataforma elegida de manera segura.

---

## Clasificación SDD

**Grande / alto riesgo** según [SDD-HYBRID-GUIDE.md](../../docs/agents/SDD-HYBRID-GUIDE.md): integra tres APIs externas, crea una nueva superficie REST, maneja credenciales e invitaciones sensibles y produce un evento de dominio. Exige `plan.md`, `tasks.md`, `checklist` y `analyze` antes de implementar.

## Contexto y alcance

### Hechos verificados

| Hecho | Evidencia |
|---|---|
| El monolito solo soporta Zoom; no existe selección de proveedor. | `[VERIFIED]` `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/zoom/ZoomMeetingClient.java:18` y `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/MentorshipController.java:71`. |
| El contrato REST canónico ya define `POST /:id/live-room` con `provider` y timeout de 4 s. | `[VERIFIED]` [`docs/contracts/mentorships/rest-api.md`](../../docs/contracts/mentorships/rest-api.md#L48). |
| El evento `MentorshipLiveRoomCreated` está definido con `assignmentId, provider, fallbackUsed` y prohíbe secretos. | `[VERIFIED]` [`docs/contracts/mentorships/events.md`](../../docs/contracts/mentorships/events.md#L82). |
| La máquina de estados limita el aula a `SCHEDULED` e `IN_PROGRESS`; los estados terminales bloquean el acceso. | `[VERIFIED]` [`docs/contracts/mentorships/state-machine.md`](../../docs/contracts/mentorships/state-machine.md). |
| HU-MNT-07 pertenece al servicio NestJS y el monolito no recibe features nuevas de mentorías. | `[VERIFIED]` [`docs/adr/0006-hybrid-monolith-and-services.md`](../../docs/adr/0006-hybrid-monolith-and-services.md) y [`docs/contracts/mentorships/gap-matrix.md`](../../docs/contracts/mentorships/gap-matrix.md#L16). |
| El servicio NestJS existe en el repositorio como `BackendNest.js/` (NestJS 12, ESM, TypeORM, Vitest) y es donde se implementa esta HU. | `[VERIFIED]` árbol actual de `BackendNest.js/`. |

### Dentro del alcance

- Crear, reutilizar y consultar la sala en vivo de una asignación `LIVE` con uno de estos proveedores: `ZOOM`, `MEET` (Google Meet) y `DISCORD`.
- Selección explícita del proveedor por parte del Provider dueño de la oferta o de un Admin.
- Idempotencia estricta: máximo una sala por asignación.
- Datos de conexión diferenciados para Provider (anfitrión) y Customer (participante), sin filtrar secretos.
- Comportamiento determinista ante configuración ausente, errores del proveedor y timeouts.
- Registro de la sala y emisión de `MentorshipLiveRoomCreated` a través del outbox de HU-MNT-08.

### Fuera del alcance

- `CUSTOM_URL` y circuit breaker: corresponden a **HU-MNT-09**.
- Validación de ventana de acceso y ocultamiento de existencia por propietario: corresponden a **HU-MNT-04**; esta spec consume ese resultado.
- Publicación y persistencia del evento en Kafka: corresponden a **HU-MNT-08**.
- Grabación, transcripción, asistencia, facturación o borrado automático de salas al terminar.
- Cambio de proveedor después de creada la sala y salas recurrentes.
- Interfaz Angular del aula; solo se define el contrato de datos que consumirá.

## Decisiones de contrato

La HU #89 y el contrato canónico usan nombres distintos. Esta spec adopta el contrato como fuente de verdad y normaliza la nomenclatura:

| Tema | HU #89 | Contrato canónico | Decisión propuesta |
|---|---|---|---|
| Ruta | `POST /api/v1/mentorships/:id/live-session` | `POST /:id/live-room` | Usar `POST /api/v1/mentorships/:id/live-room`. |
| Campo de proveedor | `liveProvider` | `provider` | Usar `provider`. |
| Google Meet | `GOOGLE_MEET` | `MEET` | Usar `MEET`; `GOOGLE_MEET` no se expone en la API. |
| Valores | Zoom, Meet, Discord | `ZOOM \| MEET \| DISCORD \| CUSTOM_URL` | MNT-07 implementa los tres primeros; `CUSTOM_URL` es de MNT-09. |

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear o reutilizar la sala de una mentoría en vivo (Priority: P1)

Como Provider dueño de una oferta `LIVE`, quiero solicitar la sala indicando el proveedor, para tener un único punto de acceso en vivo sin duplicar reuniones si repito la solicitud.

**Why this priority**: Es el núcleo de la HU: sin creación idempotente y segura no existe sala que el Customer pueda usar. Zoom se incluye como primer adaptador porque ya existe esa integración en el monolito y permite migrar sin perder la capacidad actual.

**Independent Test**: Con un adaptador Zoom simulado, un Provider dueño crea la sala de una asignación `SCHEDULED`; la primera llamada devuelve `201` y la segunda devuelve `200` con los mismos datos sin ejecutar una segunda creación externa.

**Acceptance Scenarios**:

1. **Given** una asignación propia en estado `SCHEDULED`, `mode = LIVE` y sin sala
   **When** el Provider dueño envía `POST /api/v1/mentorships/{id}/live-room` con `{ "provider": "ZOOM" }`
   **Then** el servicio crea una única reunión en Zoom y responde `201` con el proveedor y los datos de conexión del anfitrión.
2. **Given** una asignación que ya tiene una sala `ZOOM`
   **When** el Provider dueño repite la misma solicitud con `provider = ZOOM`
   **Then** el servicio responde `200` con la sala existente y no invoca de nuevo a Zoom.
3. **Given** una asignación `mode = ASYNCHRONOUS`
   **When** se solicita una sala
   **Then** responde `409` con código `LIVE_ROOM_NOT_APPLICABLE` y no se persiste ni se llama a ningún proveedor.
4. **Given** una asignación en estado terminal (`COMPLETED`, `CANCELLED`, `EXPIRED` o `NO_SHOW`)
   **When** se solicita una sala
   **Then** responde `409` con código `INVALID_STATE_TRANSITION`.
5. **Given** una asignación de otro Provider
   **When** un Provider ajeno intenta crear la sala
   **Then** responde `404` con código `NOT_FOUND`, sin revelar que la asignación existe.
6. **Given** dos solicitudes concurrentes para la misma asignación
   **When** ambas llegan al servicio con el mismo proveedor
   **Then** solo una crea la sala externa y ambas responden con el mismo resultado.

---

### User Story 2 - Entrega segura de datos de conexión por rol (Priority: P1)

Como Customer con acceso vigente, quiero recibir únicamente lo necesario para entrar a la sala; como Provider, quiero recibir lo necesario para iniciarla como anfitrión, sin que ninguno reciba secretos del otro.

**Why this priority**: Una sala creada pero inutilizable o que expone credenciales no cumple la HU. La seguridad de los datos de conexión es parte del MVP.

**Independent Test**: Con una sala de cada proveedor simulada, se consulta el acceso como Customer dentro de la ventana y se verifica que recibe solo datos de participante; se repite como Provider y se verifica que recibe el mecanismo de anfitrión. Fuera de la ventana o con estado terminal se rechaza.

**Acceptance Scenarios**:

1. **Given** una asignación `LIVE` con sala y ventana activa
   **When** el Customer dueño consulta `GET /api/v1/mentorships/{id}/access`
   **Then** recibe `provider` y los datos de ingreso como participante, sin credenciales de anfitrión.
2. **Given** una sala `ZOOM`
   **When** el Provider dueño repite la solicitud de creación de forma idempotente
   **Then** recibe el mismo mecanismo de inicio como anfitrión (por ejemplo, URL de inicio o firma de rol anfitrión) que nunca se entrega al Customer.
3. **Given** una asignación fuera de la ventana `[accessStartsAt, accessEndsAt)` o en estado terminal
   **When** el Customer consulta el acceso
   **Then** el servicio rechaza la consulta con el código y estado definidos por HU-MNT-04.
4. **Given** una sala creada
   **When** un listado, un log o un evento de dominio incluye la asignación
   **Then** no aparecen `passcode`, firmas, tokens OAuth, URLs de inicio de anfitrión ni invitaciones de Discord.
5. **Given** una sala `DISCORD` cuyo invite fue consumido
   **When** el Customer vuelve a consultar el acceso dentro de la ventana
   **Then** el servicio entrega una invitación vigente o la regenera, sin dejar al Customer sin acceso.

---

### User Story 3 - Crear sala de Google Meet (Priority: P2)

Como Provider, quiero crear la sesión en Google Meet desde la misma API, para usar el calendario de mi organización sin salir de SkillBridge.

**Why this priority**: Es el segundo proveedor y el que más depende de la identidad y el calendario configurado. Aporta valor, pero la HU sigue siendo utilizable con Zoom.

**Independent Test**: Con la API de Calendar simulada, se crea una sala `MEET`; se verifica que se envía una solicitud de conferencia, se persiste el enlace `meet.google.com` y una repetición no crea un segundo evento.

**Acceptance Scenarios**:

1. **Given** una asignación `LIVE` sin sala y credenciales de Google configuradas
   **When** el Provider dueño solicita `provider = MEET`
   **Then** el servicio crea un evento de calendario con conferencia y responde `201` con el enlace de Meet y el identificador del evento.
2. **Given** una sala `MEET` ya creada
   **When** el Provider repite la solicitud
   **Then** responde `200` con el mismo enlace e identificador, sin crear otro evento.
3. **Given** que Google no está configurado o rechaza las credenciales
   **When** se solicita `MEET`
   **Then** responde `503` con `LIVE_PROVIDER_UNAVAILABLE`, no persiste una sala parcial y no simula una reunión.
4. **Given** que Calendar crea el evento pero falla la persistencia local
   **When** el Provider reintenta
   **Then** el servicio reconcilia por identificador de asignación antes de crear otro evento.
5. **Given** una sala `MEET`
   **When** el Customer consulta el acceso dentro de la ventana
   **Then** recibe el enlace de Meet, sin tokens OAuth ni datos del calendario.

---

### User Story 4 - Crear canal e invitación de Discord (Priority: P3)

Como Provider, quiero crear un canal de voz temporal y una invitación controlada en Discord, para dar la sesión sin compartir un enlace permanente.

**Why this priority**: Es el proveedor de menor prioridad para el MVP, pero completa la promesa multi-proveedor.

**Independent Test**: Con la API de Discord simulada, se crea un canal `DISCORD`; se verifica que el canal es de voz, que la invitación expira al final de la ventana y que una repetición no crea otro canal.

**Acceptance Scenarios**:

1. **Given** una asignación `LIVE` sin sala y un bot con permisos en el servidor configurado
   **When** el Provider dueño solicita `provider = DISCORD`
   **Then** el servicio crea un canal de voz temporal y una invitación y responde `201` con el canal y la invitación.
2. **Given** un canal `DISCORD` ya creado
   **When** el Provider repite la solicitud
   **Then** responde `200` con el mismo canal e invitación vigente, sin duplicar el canal.
3. **Given** que el bot no existe, no está en el servidor o carece de permisos
   **When** se solicita `DISCORD`
   **Then** responde `503` con `LIVE_PROVIDER_UNAVAILABLE`, no deja un canal huérfano y el mensaje indica qué permiso falta sin revelar el token.
4. **Given** una sala `DISCORD`
   **When** el Customer consulta el acceso dentro de la ventana
   **Then** recibe la invitación vigente, sin el token del bot ni permisos administrativos.
5. **Given** que Discord aplica un límite de tasa
   **When** el servicio recibe la respuesta del proveedor
   **Then** respeta el tiempo indicado, responde de forma controlada y no entra en un bucle de reintentos.

---

### Edge Cases

- Proveedor no soportado o ausente en el cuerpo: `400 VALIDATION_ERROR`.
- `CUSTOM_URL` solicitado antes de HU-MNT-09: `400 UNSUPPORTED_LIVE_PROVIDER`.
- Asignación inexistente o de otro Provider: `404 NOT_FOUND`.
- Usuario con rol `CUSTOMER`: `403 FORBIDDEN` al intentar crear o consultar datos de anfitrión.
- Proveedor externo responde `5xx`, `429` o supera 4 s: `503 LIVE_PROVIDER_UNAVAILABLE` o `504 LIVE_PROVIDER_TIMEOUT`, sin sala persistida.
- Timeout ambiguo después de que el proveedor pudo haber creado la sala: el reintento no debe duplicar; se reconcilia por asignación antes de crear.
- Sala existente de un proveedor distinto al solicitado: `409 LIVE_ROOM_ALREADY_EXISTS` con el proveedor actual (decisión pendiente; ver §Decisiones).
- Credenciales de un proveedor ausentes: solo ese proveedor queda indisponible; el servicio no cae ni usa otro proveedor automáticamente.
- Correo del Provider o del Customer no disponible para Google Meet: no se genera un enlace público por defecto; se aplica la decisión pendiente de identidad/calendario.
- Invitación de Discord expirada o consumida: se regenera o se entrega una vigente; nunca se devuelve una invitación caducada.
- Fallo de persistencia después de crear la sala externa: se registra la inconsistencia, se reconcilia y no se responde `201` hasta que el estado local sea consistente.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El servicio DEBE aceptar la creación de sala solo para asignaciones con `mode = LIVE` y estado `SCHEDULED` o `IN_PROGRESS`. Cualquier otro modo o estado DEBE rechazarse sin llamar al proveedor externo.
- **FR-002**: Solo el PROVIDER dueño de la oferta asociada o un ADMIN PUEDEN crear la sala. Un Provider ajeno o una asignación inexistente DEBE responder `404`; un rol sin permiso DEBE responder `403`.
- **FR-003**: El proveedor DEBE seleccionarse explícitamente en el cuerpo con el campo `provider`. MNT-07 DEBE soportar `ZOOM`, `MEET` y `DISCORD`. `CUSTOM_URL` queda fuera de esta HU y DEBE rechazarse hasta que HU-MNT-09 lo defina.
- **FR-004**: El endpoint canónico DEBE ser `POST /api/v1/mentorships/{id}/live-room`. La primera creación DEBE responder `201`; una repetición idempotente DEBE responder `200`.
- **FR-005**: DEBE existir como máximo una sala por asignación. Repetir la solicitud con el mismo proveedor DEBE devolver la sala existente sin ninguna llamada externa adicional. Solicitar un proveedor distinto con una sala ya creada DEBE rechazarse con `409 LIVE_ROOM_ALREADY_EXISTS` (decisión pendiente).
- **FR-006**: La sala DEBE persistir, como mínimo: `assignmentId`, `provider`, identificador externo de la sala, datos de ingreso de participante, datos de inicio de anfitrión cuando apliquen, marcas de tiempo y estado de sincronización. La restricción única sobre `assignmentId` DEBE impedir duplicados a nivel de base de datos.
- **FR-007**: Zoom DEBE crearse con OAuth de servidor a servidor y credenciales de plataforma. La reunión DEBE respetar la ventana de acceso, configurar sala de espera y no permitir ingreso antes del anfitrión. El Provider DEBE recibir un mecanismo de inicio como anfitrión y el Customer uno de participante.
- **FR-008**: Google Meet DEBE crearse mediante un evento de calendario con solicitud de conferencia (`conferenceData`). El enlace resultante DEBE ser `meet.google.com/...` y el evento DEBE quedar asociado a la asignación. La política de calendario e invitados queda pendiente de decisión.
- **FR-009**: Discord DEBE crearse mediante un bot en el servidor y categoría configurados. El canal DEBE ser de voz, temporal y nombrado con un identificador no sensible de la asignación. La invitación DEBE expirar al final de `accessEndsAt` y tener un número limitado de usos; el detalle exacto queda pendiente de decisión.
- **FR-010**: `GET /api/v1/mentorships/{id}/access` DEBE entregar al CUSTOMER dueño, dentro de la ventana y con estado permitido, el proveedor y los datos de ingreso de participante. La validación de ventana, estado y ocultamiento de existencia es responsabilidad de HU-MNT-04.
- **FR-011**: Los datos de anfitrión (URL de inicio, firma con rol anfitrión o equivalente) DEBEN entregarse solo al Provider dueño o a un Admin, nunca al Customer, a un listado, a un log o a un evento.
- **FR-012**: DEBE prohibirse en logs, listados, respuestas ajenas al rol y eventos de dominio: `passcode`, firmas, tokens OAuth, `start_url`, tokens de bot e invitaciones de Discord. La verificación DEBE cubrir los tres proveedores.
- **FR-013**: Si un proveedor no está configurado o rechaza la autenticación, el servicio DEBE responder `503 LIVE_PROVIDER_UNAVAILABLE` y no DEBE simular, falsear ni sustituir automáticamente por otro proveedor.
- **FR-014**: Toda llamada externa DEBE tener un timeout máximo de 4 s, conforme al contrato. Los errores de timeout DEBEN mapearse a `504 LIVE_PROVIDER_TIMEOUT`; los límites de tasa DEBEN respetarse y no reintentarse de forma ilimitada.
- **FR-015**: La creación DEBE ser segura ante concurrencia. Dos solicitudes simultáneas para la misma asignación DEBEN producir una sola sala externa y la misma respuesta.
- **FR-016**: Si la sala externa se crea pero falla la persistencia local, el servicio DEBE reconciliar por asignación antes de permitir otro intento; no DEBE devolver éxito hasta que el estado local y el externo sean consistentes.
- **FR-017**: La creación de la sala y el registro del evento en `outbox_events` DEBEN ser atómicos. El evento `MentorshipLiveRoomCreated` DEBE contener `assignmentId`, `provider` y `fallbackUsed`; en MNT-07 `fallbackUsed` DEBE ser `false`. La publicación efectiva corresponde a HU-MNT-08.
- **FR-018**: El servicio DEBE propagar el `X-Correlation-Id` recibido en la respuesta, los logs y el evento. Las llamadas externas DEBEN registrar el mismo identificador en la traza local.
- **FR-019**: La ausencia de credenciales de un proveedor NO DEBE impedir el arranque del servicio ni el uso de los demás proveedores. La configuración DEBE declararse por variables de entorno o gestor de secretos; nunca en el repositorio.
- **FR-020**: Los secretos recuperables de una sala (passcode, URL de inicio, invitación) DEBEN almacenarse cifrados en reposo o en un almacén de secretos, con acceso restringido al servicio.
- **FR-021**: Las pruebas DEBEN usar servidores HTTP simulados para Zoom, Google Calendar y Discord. La suite no DEBE requerir credenciales reales ni crear recursos externos.

### Non-Functional Requirements

- **NFR-001**: La creación de sala DEBE acotar la latencia al timeout externo de 4 s más el tiempo de persistencia; `GET /access` DEBE resolverse solo con datos locales.
- **NFR-002**: La funcionalidad DEBE respetar la prohibición de mocks en producción de la Constitución (Artículo V): un fallo de proveedor se reporta como error, nunca como éxito simulado.
- **NFR-003**: DEBEN existir métricas por proveedor de intentos, éxitos, fallos, timeouts y latencia, sin etiquetas con datos personales o secretos.
- **NFR-004**: Los errores DEBEN usar el cuerpo uniforme `{ code, message, correlationId }` definido en [`rest-api.md`](../../docs/contracts/mentorships/rest-api.md).

### Key Entities

- **MentorshipAssignment**: asignación existente; aporta `id`, `bookingId`, `customerId`, `offeringId`, `mode`, `status`, ventana `[accessStartsAt, accessEndsAt)` y progreso. No se modifica su semántica.
- **LiveRoom**: nueva relación 1:1 con la asignación. Contiene proveedor, identificadores externos, datos de ingreso, datos de anfitrión y estado de sincronización. `assignmentId` es único.
- **LiveProviderConfig**: configuración por entorno para Zoom, Google y Discord. No se persiste en la base de datos ni se versiona con valores reales.
- **MentorshipLiveRoomCreated**: evento de dominio publicado vía outbox; solo identificadores, proveedor y `fallbackUsed`, sin secretos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un Provider puede crear una sala para una asignación `LIVE` con cada uno de los tres proveedores configurados y obtener datos de anfitrión utilizables.
- **SC-002**: El 100 % de las repeticiones idempotentes produce cero reuniones, eventos o canales adicionales en los proveedores externos.
- **SC-003**: El 100 % de las pruebas de fuga verifica cero aparición de `passcode`, firmas, tokens, `start_url` o invitaciones en listados, logs y eventos.
- **SC-004**: El 100 % de los fallos de configuración, autenticación, `5xx`, `429` y timeout se traduce en un error explícito (`503`/`504`), sin sala persistida ni respuesta simulada.
- **SC-005**: Un Customer con acceso vigente obtiene los datos de ingreso en una sola consulta, y el 100 % de los intentos fuera de ventana o en estado terminal se rechazan conforme a HU-MNT-04.
- **SC-006**: Diez solicitudes concurrentes para la misma asignación producen exactamente una sala externa y respuestas consistentes.
- **SC-007**: La suite de aceptación se ejecuta con proveedores simulados, sin credenciales reales, y cubre los escenarios P1, P2 y P3.
- **SC-008**: Cada creación exitosa registra `MentorshipLiveRoomCreated` con `provider` correcto y `fallbackUsed = false`, sin secretos.

## Assumptions

### Hechos adicionales y dependencias

- `BackendNest.js/` es el dueño de la sala y de su persistencia; el monolito no se modifica para esta HU `[VERIFIED]` (ADR-0006).
- La autenticación es JWT RS256 emitido por el monolito; el servicio valida con `JWT_PUBLIC_KEY` y usa los claims `uid`, `email` y `role` `[VERIFIED]` ([`security.md`](../../docs/contracts/mentorships/security.md)).
- HU-MNT-02 ya crea asignaciones `SCHEDULED` con `mode` y ventana; MNT-07 no las crea `[INFERRED]` a partir de los contratos.
- HU-MNT-04 ya rechaza accesos fuera de ventana y oculta asignaciones ajenas; MNT-07 no duplica esa lógica `[INFERRED]`.
- HU-MNT-08 provee la tabla `outbox_events` y el publicador; MNT-07 solo escribe el registro en la misma transacción `[VERIFIED]` en el contrato de eventos.

### Supuestos de configuración

- Las credenciales son de plataforma por proveedor: Zoom (client ID, client secret, account ID, SDK key/secret), Google (cuenta de servicio u OAuth con calendario configurado) y Discord (token de bot, guild y categoría).
- El servicio puede arrancar con uno o más proveedores configurados; los no configurados responden `503`.
- La sala se puede preparar desde `SCHEDULED`, antes de que empiece la ventana; el acceso del Customer sigue limitado por HU-MNT-04.
- Una asignación no cambia de proveedor una vez creada la sala.

### Incógnitas

- `[UNKNOWN]` Modelo de identidad para Google Meet: si el evento se crea en un calendario de plataforma o en el calendario del Provider, y cómo se restringe el enlace a los participantes.
- `[UNKNOWN]` Disponibilidad del correo del Provider en el `mentorship-service`; el evento `PaymentApproved` v2 incluye `customerEmail`, pero no se verificó un campo equivalente para el Provider.
- `[UNKNOWN]` Límites y cuotas reales de las cuentas de Zoom, Google y Discord en los entornos de despliegue.
- `[UNKNOWN]` Si el frontend actual podrá consumir los datos de conexión sin cambios; hoy espera campos planos de Zoom (`liveRoomUrl`, `zoomMeetingId`, `zoomPasscode`, `zoomSdkKey`, `zoomSignature`) `[VERIFIED]` en `frontend/src/app/core/mentorship.service.ts:9`.

## Decisiones pendientes

Las siguientes decisiones requieren aprobación humana antes de `plan.md`. Se incluye una recomendación.

1. **[DECIDIR] Proveedor inmutable y conflicto:** ¿qué ocurre si se solicita un proveedor distinto cuando ya existe sala? **Recomendado:** `409 LIVE_ROOM_ALREADY_EXISTS` con el proveedor actual; no cambiar de proveedor automáticamente.
2. **[DECIDIR] Identidad de anfitrión en Google Meet:** ¿calendario de plataforma con Provider invitado o OAuth por Provider? **Recomendado:** OAuth por Provider (o calendario del Provider) para que sea el organizador; si no es viable, calendario de plataforma con política de acceso restringida y Provider invitado.
3. **[DECIDIR] Restricción de acceso a Meet:** ¿enlace solo para invitados, dominio permitido o público con enlace? **Recomendado:** solo invitados cuando los correos estén disponibles; nunca público por defecto.
4. **[DECIDIR] Invitación de Discord:** la HU pide "un solo uso", pero eso rompe la reentrada del Customer. **Recomendado:** invitación válida hasta `accessEndsAt`, con usos limitados y regeneración si se consume; no usar un enlace permanente.
5. **[DECIDIR] Persistencia de secretos:** ¿cifrado en base de datos o gestor de secretos? **Recomendado:** cifrado en reposo para passcode/URL de inicio/invitación, con clave gestionada por entorno.
6. **[DECIDIR] Nombre del canal de Discord:** **Recomendado:** prefijo no sensible + `assignmentId` corto, sin correo ni nombre del Customer.

## Dependencies & Out of Scope

### Dependencies

- **HU-MNT-01**: servicio NestJS (`BackendNest.js`), TypeORM, PostgreSQL propio y configuración estricta.
- **HU-MNT-02**: asignaciones `SCHEDULED` con `mode`, ventana y `bookingId` único.
- **HU-MNT-03 / HU-MNT-04**: estados, progreso y control de acceso por propietario y ventana.
- **HU-MNT-08**: outbox y publicación de `MentorshipLiveRoomCreated` en Kafka.
- **HU-MNT-09**: circuit breaker, `CUSTOM_URL` y degradación controlada. MNT-07 fija el timeout y el error explícito, pero no implementa el breaker.
- **Contratos**: [`rest-api.md`](../../docs/contracts/mentorships/rest-api.md), [`events.md`](../../docs/contracts/mentorships/events.md), [`state-machine.md`](../../docs/contracts/mentorships/state-machine.md), [`security.md`](../../docs/contracts/mentorships/security.md).
- **ADR-0006**: aislamiento del servicio y comunicación asíncrona; sin HTTP síncrono al monolito durante el aprovisionamiento.

### Out of Scope

- `CUSTOM_URL`, circuit breaker y fallback entre proveedores (HU-MNT-09).
- Creación de asignaciones, validación de pagos y publicación de eventos (HU-MNT-02 y HU-MNT-08).
- Reglas de ventana, ocultamiento de existencia y autorización de acceso (HU-MNT-04).
- Recursos de mentoría y archivos (HU-MNT-05 / HU-MNT-06).
- Grabación, transcripción, asistencia, analítica de sesión, facturación y eliminación automática.
- UI Angular del aula y rediseño del componente de mentorías.
- Reuniones recurrentes y cambio de proveedor después de creada la sala.

## Trazabilidad resumida

| Necesidad | Historias | Requisitos |
|---|---|---|
| Creación idempotente y autorizada | US1 | FR-001 a FR-007, FR-014 a FR-017 |
| Datos de conexión seguros por rol | US2 | FR-010 a FR-012, FR-020 |
| Google Meet | US3 | FR-008, FR-013, FR-016, FR-021 |
| Discord | US4 | FR-009, FR-013, FR-014, FR-021 |
| Observabilidad y consistencia | US1–US4 | FR-017 a FR-019, NFR-001 a NFR-004 |
