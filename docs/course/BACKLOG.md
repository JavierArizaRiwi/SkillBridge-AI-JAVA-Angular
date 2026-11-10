# Backlog para las células

Este documento separa las funcionalidades que ya incluye el proyecto base de las mejoras que pueden implementar las células. Las HU propuestas no son un plan cerrado: cada equipo debe inspeccionar el código, descubrir oportunidades y añadir al menos una HU propia.

## HU implementadas en el proyecto base

### HU-01 — Consultar el catálogo público

**Como** visitante, **quiero** consultar los servicios activos, **para** conocer las opciones disponibles.

**Estado:** Implementada.

**Incluye:** endpoint de consulta, persistencia con PostgreSQL y caché de catálogo con Redis.

### HU-02 — Crear una cuenta e iniciar sesión

**Como** usuario, **quiero** registrarme e iniciar sesión, **para** acceder a las operaciones protegidas.

**Estado:** Implementada.

**Incluye:** registro e inicio de sesión, contraseñas con BCrypt y emisión/validación de JWT. El registro crea cuentas con rol `CUSTOMER`; la autorización granular por roles queda pendiente.

### HU-03 — Crear una reserva

**Como** cliente autenticado, **quiero** reservar un servicio para una fecha futura, **para** solicitar una sesión.

**Estado:** Implementada.

**Incluye:** validación de reglas de negocio, persistencia de la reserva y publicación del evento `BookingCreated`.

### HU-04 — Procesar eventos de reserva de forma asíncrona

**Como** sistema, **quiero** procesar el evento de una reserva fuera de la petición HTTP, **para** desacoplar el flujo de reserva de sus efectos posteriores.

**Estado:** Implementada como extensión inicial.

**Incluye:** publicación y consumo con RabbitMQ, configuración de reintentos y Dead Letter Queue. El consumidor actual registra el evento en logs; todavía no persiste ni envía una notificación al cliente.

### HU-05 — Solicitar recomendaciones de servicios con Gemini

**Como** usuario autenticado, **quiero** recibir recomendaciones basadas en mi objetivo y el catálogo, **para** encontrar servicios relevantes.

**Estado:** Implementada.

**Incluye:** endpoint de recomendaciones, integración Spring AI con Google Gemini, modelo configurable y API key solo en backend. La disponibilidad depende de la cuota y el servicio externo de Google.

### HU-06 — Usar la aplicación localmente con Docker Compose

**Como** integrante de una célula, **quiero** levantar la aplicación y sus dependencias con Docker Compose, **para** trabajar con un entorno común.

**Estado:** Implementada.

**Incluye:** Angular servido por Nginx, backend Spring Boot, PostgreSQL, Redis, RabbitMQ, health checks y configuración opcional de Prometheus/Grafana.

### HU-07 — Probar reglas de negocio y persistencia

**Como** desarrollador, **quiero** contar con pruebas automatizadas iniciales, **para** detectar regresiones al cambiar el código.

**Estado:** Parcialmente implementada.

**Incluye:** pruebas unitarias de servicios de reservas y catálogo, y una prueba de persistencia preparada con Testcontainers. Ampliar y asegurar su ejecución en el entorno del equipo es trabajo pendiente.

## Propuesta de Sprint — Dos semanas

**Duración:** 10 días hábiles.  
**Meta del Sprint:** permitir que un cliente autenticado consulte sus reservas y cancele una reserva permitida, protegiendo los datos de otros clientes. El equipo también investigará y corregirá un bug real si encuentra uno reproducible dentro de la capacidad acordada.

Las estimaciones son iniciales. En Sprint Planning, el equipo debe estimar las HU, revisar su capacidad y acordar el Sprint Backlog. No es necesario comprometer todas las opciones de continuación que aparecen más adelante.

### Trabajo priorizado por el Product Owner

#### HU-08 — Consultar mis reservas

**Como** cliente autenticado, **quiero** consultar mis reservas, **para** revisar las sesiones que he solicitado.

**Criterios de aceptación:**
- La API devuelve las reservas asociadas al usuario autenticado.
- Un usuario no puede consultar reservas de otra cuenta.
- La interfaz muestra un estado vacío cuando todavía no existen reservas.

**Tareas sugeridas:**
- Definir el contrato del endpoint y el orden de las reservas.
- Añadir el puerto de salida, caso de uso y consulta de persistencia por usuario.
- Exponer el endpoint protegido, por ejemplo `GET /api/bookings/me`.
- Crear la vista Angular y sus estados de carga, error y lista vacía.
- Probar usuario con reservas, usuario sin reservas y aislamiento entre cuentas.

#### HU-09 — Cancelar una reserva propia

**Como** cliente autenticado, **quiero** cancelar una reserva propia que todavía pueda modificarse, **para** liberar una sesión que ya no necesito.

**Criterios de aceptación:**
- Solo el propietario puede cancelar la reserva.
- Las reglas definen en qué estados o momentos se permite cancelar.
- La cancelación conserva el registro y devuelve un resultado claro.
- Intentar cancelar una reserva ajena o no cancelable no cambia sus datos.

**Tareas sugeridas:**
- Proponer estados y transiciones válidas, y acordarlas con el Product Owner.
- Añadir el caso de uso, puerto y operación de persistencia.
- Exponer una operación protegida para cancelar.
- Incorporar la acción y confirmación en la interfaz.
- Probar transición válida, transición inválida y acceso entre usuarios.

### Trabajo de calidad acordado por el equipo

#### BUG-01 — Reproducir y corregir un defecto confirmado

No se asigna un defecto inventado: el equipo debe encontrarlo y reproducirlo durante la exploración inicial. Si no identifica uno con evidencia, puede sustituirlo por una mejora de calidad priorizada con el Product Owner.

**Criterios de aceptación:**
- Existe un reporte con pasos, comportamiento esperado y comportamiento observado.
- Una prueba demuestra el fallo antes de corregirlo o cubre la regresión.
- El cambio corrige la causa raíz y no expone secretos ni datos personales.

**Tareas sugeridas:**
- Recorrer los flujos de autenticación, catálogo, reservas y recomendaciones.
- Registrar candidatos a bug y priorizarlos por impacto y reproducibilidad.
- Acordar el bug seleccionado y su alcance con el Product Owner.
- Añadir la prueba, corregir, revisar regresiones y documentar el resultado.

### Calendario orientativo del Sprint

| Día | Enfoque sugerido | Evidencia esperada |
|---|---|---|
| 1 | Sprint Planning, lectura del sistema y triage de bugs | Meta, HU seleccionadas, capacidad y tablero acordados |
| 2 | Refinar contratos, estados de reserva y diseño de pruebas | Decisiones registradas y tareas listas |
| 3–4 | Implementar consulta de reservas en backend y pruebas | Endpoint protegido probado |
| 5 | Integrar consulta con Angular y revisar avance | Flujo navegable de consulta |
| 6–7 | Implementar reglas y operación de cancelación | Cancelación segura probada |
| 8 | Integrar la acción de cancelación en Angular | Flujo completo de consulta/cancelación |
| 9 | Resolver BUG-01 o mejora acordada y ejecutar regresiones | Defecto reproducido y corrección demostrable |
| 10 | Estabilizar, Sprint Review y Retrospective | Incremento presentado, feedback y acciones de mejora |

El Daily Scrum se realiza cada día de trabajo; no es una reunión de reporte al instructor. El equipo inspecciona el avance hacia la meta y adapta su plan.

### Opciones para el Product Backlog siguiente

Estas historias no forman parte automáticamente del compromiso del Sprint. El Product Owner y el equipo las refinan y priorizan después de revisar el incremento y la capacidad disponible.

#### HU-10 — Administrar servicios como proveedor

**Como** proveedor, **quiero** crear, editar y desactivar mis servicios, **para** mantener vigente mi oferta.

**Criterios de aceptación:** un proveedor solo puede modificar sus propios servicios; los servicios inactivos no aparecen en el catálogo público; las modificaciones aplican validaciones y mantienen coherente el caché.

**Tareas candidatas:** revisar modelo y roles; definir reglas y contratos API; implementar persistencia y autorización por propietario; diseñar y probar la invalidación del caché.

#### HU-11 — Persistir y consultar notificaciones

**Como** cliente, **quiero** conocer el estado de las notificaciones de mis reservas, **para** saber si fueron procesadas.

**Criterios de aceptación:** el consumidor persiste el resultado; una entrega repetida no duplica efectos; los fallos se pueden diagnosticar sin registrar datos sensibles.

**Tareas candidatas:** definir modelo, estados y retención; persistir desde el consumidor RabbitMQ; diseñar y probar idempotencia; añadir observabilidad.

#### HU-12 — Explicar errores del proveedor de IA

**Como** usuario, **quiero** recibir un mensaje claro cuando Gemini no puede atender una recomendación, **para** saber si debo intentarlo más tarde o revisar la configuración.

**Criterios de aceptación:** los errores de cuota, configuración y disponibilidad se distinguen; el frontend muestra una explicación segura y útil; los logs no revelan la API key ni el prompt completo.

**Tareas candidatas:** revisar excepciones del adaptador Spring AI; definir respuestas y estados HTTP; actualizar el manejo de errores de Angular; probar casos de cuota agotada y proveedor no disponible.

### Definición de Terminado para el Sprint

- Criterios de aceptación verificados por el equipo y Product Owner.
- Pruebas relevantes agregadas y ejecutadas en el entorno disponible.
- Cambios revisados por otro integrante y compatibles con la arquitectura hexagonal.
- Sin secretos ni datos sensibles en código, respuestas o logs.
- README o documentación de API actualizados cuando cambie el contrato.
- Incremento integrado y demostrable al cierre del Sprint.

### Descubrimiento propio de cada célula

- Cada integrante revisa una parte distinta del sistema y propone un bug, riesgo o mejora con evidencia del código.
- El equipo añade sus propuestas al Product Backlog, evita duplicados y las ordena por valor y riesgo.
- En la Retrospective, el equipo registra una acción concreta para mejorar su forma de trabajo en el siguiente Sprint.
