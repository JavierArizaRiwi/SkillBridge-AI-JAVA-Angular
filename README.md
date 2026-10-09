<div align="center">

#  SkillBridge AI

**Plataforma de mentorías y servicios profesionales con recomendaciones de IA multimodal, arquitectura hexagonal y mensajería orientada a eventos.**

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-6DB33F?logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-20-DD0031?logo=angular&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-4-FF6600?logo=rabbitmq&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.8-231F20?logo=apachekafka&logoColor=white)
![Docker](https://img.shields.io/badge/Docker%20Compose-ready-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

</div>

![Inicio](images/Login.png)
![Login_Registrarse](images/Login_Registrarse.png)

---

## Tabla de contenidos

1. [Descripción general](#-descripción-general)
2. [Características principales](#-características-principales)
3. [Arquitectura](#-arquitectura)
4. [Stack tecnológico](#-stack-tecnológico)
5. [Estructura del repositorio](#-estructura-del-repositorio)
6. [Inicio rápido](#-inicio-rápido)
7. [Servicios y puertos](#-servicios-y-puertos)
8. [Usuarios de prueba](#-usuarios-de-prueba)
9. [Configuración](#-configuración)
10. [API REST](#-api-rest)
11. [Flujos clave](#-flujos-clave)
12. [Mensajería: RabbitMQ y Kafka](#-mensajería-rabbitmq-y-kafka)
13. [Servicio de auditoría](#-servicio-de-auditoría)
14. [Inteligencia artificial](#-inteligencia-artificial)
15. [Seguridad](#-seguridad)
16. [Observabilidad](#-observabilidad)
17. [Pruebas](#-pruebas)
18. [CI/CD](#-cicd)
19. [Despliegue](#-despliegue)
20. [Desarrollo local sin Docker](#-desarrollo-local-sin-docker)
21. [Solución de problemas](#-solución-de-problemas)
22. [Limitaciones conocidas y roadmap](#-limitaciones-conocidas-y-roadmap)
23. [Documentación adicional](#-documentación-adicional)
24. [Licencia](#-licencia)

---

##  Descripción general

![Servicios](images/Servicios.png)

![Mis Servicios](images/Mis_servicios.png)



**SkillBridge AI** conecta a **proveedores** de servicios profesionales (mentorías de Java, Angular, arquitectura, DevOps, cloud, UX, etc.) con **clientes** que quieren descubrirlos, reservarlos y pagarlos. Un asistente de IA recomienda servicios del catálogo a partir de lo que el usuario escribe, **dice con su voz** o **muestra en una imagen**.

El dominio de negocio es deliberadamente simple: sirve como vehículo para aplicar, en un mismo proyecto coherente, prácticas de ingeniería de nivel profesional:

- Arquitectura **hexagonal** (puertos y adaptadores) con dominio libre de frameworks.
- **Mensajería dual**: RabbitMQ para tareas asíncronas y Kafka para eventos de negocio y auditoría.
- **Seguridad** con JWT en cookie `HttpOnly`, CSRF y control de acceso por roles.
- **Idempotencia**, trazabilidad por `Correlation ID` y control de concurrencia.
- **Observabilidad** con Prometheus y Grafana, y **CI** con GitHub Actions.

> El proyecto completo se levanta con **un solo comando** (`docker compose up --build`) sin instalar Java, Node, PostgreSQL, Redis, RabbitMQ ni Kafka.

---

##  Características principales

### Para clientes
-  Catálogo público de servicios, servido con caché **Cache-Aside** en Redis.
-  Reserva de sesiones con **clave de idempotencia** (sin reservas duplicadas por doble clic o reintentos).
-  Cancelación de reservas propias con regla de **24 horas de anticipación**, historial de estados y operación idempotente.
-  Autorización de pagos simulada, con resultado procesado de forma asíncrona vía RabbitMQ.
-  Recomendaciones de IA por **texto, voz o imagen**.

### Para proveedores
-  CRUD de servicios propios (crear, editar, activar y desactivar) con invalidación de caché.
-  Gestión de categorías.

### Para administradores
-  Administración de usuarios: listado paginado y ordenable, cambio de **rol** y **estado** (`ACTIVE` / `SUSPENDED`).
-  Reglas de protección (un admin no puede modificar su propio rol o estado) y registro de auditoría.
-  **Dashboard de auditoría** independiente con eventos de negocio casi en tiempo real.

### Transversales
-  Notificaciones asíncronas (simuladas o por **email** con Mailpit en desarrollo) con reintentos y **Dead Letter Queue**.
-  `X-Correlation-Id` propagado desde HTTP hasta los eventos de Kafka.
-  Métricas expuestas a Prometheus y dashboards en Grafana.

---

##  Arquitectura

### Vista general del sistema

```mermaid
flowchart LR
    U([Usuario]) --> NG[Nginx<br/>:8088]
    NG -->|SPA| FE[Angular 20]
    NG -->|/api/*| BE[Spring Boot API<br/>:8080]

    BE --> PG[(PostgreSQL 17)]
    BE --> RD[(Redis 8<br/>caché)]
    BE -->|tareas y notificaciones| RB{{RabbitMQ 4}}
    BE -->|eventos de negocio y auditoría| KF{{Apache Kafka}}
    BE -->|SMTP| MP[Mailpit]
    BE -->|HTTPS| AI[Gemini API]

    RB -->|consumers| BE
    KF --> AS[Audit Service<br/>:8081]
    AS --> AF[Dashboard Angular<br/>:8090]

    BE -. /actuator/prometheus .-> PR[Prometheus] --> GF[Grafana]
```

### Arquitectura hexagonal del backend

El dominio y la capa de aplicación **no conocen** JPA, Redis, RabbitMQ, Kafka, HTTP ni ningún proveedor de IA. Toda dependencia externa entra a través de **puertos** y se implementa en **adaptadores**.

```text
backend/src/main/java/com/riwi/skillbridge
│
├── domain/                      ← Núcleo: reglas de negocio puras
│   ├── model/                   Booking, Offering, UserAccount, Category, Payment*…
│   ├── policy/ · service/       OfferingAccessPolicy, UserManagementPolicy, BookingCancellationPolicy
│   ├── event/ · exception/
│
├── application/                 ← Casos de uso y contratos
│   ├── port/in/                 Puertos de entrada (casos de uso)
│   ├── port/out/                Puertos de salida (repositorios, caché, mensajería, IA, pagos…)
│   └── service/                 Implementación de los casos de uso
│
└── infrastructure/              ← Adaptadores y configuración
    ├── adapter/in/
    │   ├── rest/                Controladores, DTOs, validación, manejo de errores (RFC 7807)
    │   └── messaging/           Consumers de RabbitMQ y Kafka
    ├── adapter/out/
    │   ├── persistence/         JPA + PostgreSQL
    │   ├── cache/               Redis
    │   ├── messaging/           Publishers RabbitMQ y Kafka
    │   ├── ai/                  Gemini / OpenAI / Groq
    │   ├── notification/        Simulado / Email
    │   └── payment/             Pasarela simulada
    ├── security/                JWT, cookies, CSRF
    └── config/
```

**Ejemplo de inversión de dependencias:** el caso de uso de recomendaciones depende de `AiRecommendationPort`; `GeminiAiAdapter` u `OpenAiAiAdapter` lo implementan. Cambiar de proveedor no toca el caso de uso.

---

##  Stack tecnológico

| Capa | Tecnologías |
|---|---|
| **Backend** | Java 21 · Spring Boot 3.5.6 · Spring MVC · Spring Security · Spring Data JPA · Spring AMQP · Spring Kafka · Spring AI 1.1.8 · Bean Validation · Springdoc OpenAPI · JJWT 0.12.6 · Lombok |
| **Frontend** | Angular 20 (standalone components, router, interceptores funcionales) · Tailwind CSS 4 · RxJS |
| **Datos** | PostgreSQL 17 (+ Flyway) · Redis 8 |
| **Mensajería** | RabbitMQ 4 (con DLQ) · Apache Kafka (Confluent 7.8, modo KRaft) |
| **IA** | Google Gemini (por defecto) · OpenAI (adaptador alternativo) · Groq (transcripción de audio, opcional) |
| **Observabilidad** | Spring Actuator · Micrometer · Prometheus 3.5 · Grafana 12.1 |
| **Calidad** | JUnit 5 · Mockito · Testcontainers · JaCoCo · Karma/Jasmine · Qodana |
| **DevOps** | Docker (multi-stage) · Docker Compose · Nginx · GitHub Actions |
| **Desarrollo** | Mailpit (captura de correos) |

---

##  Estructura del repositorio

```text
SkillBridge-AI-JAVA-Angular/
├── backend/                  API principal (Spring Boot, arquitectura hexagonal)
│   ├── src/main/resources/db/migration/   Migraciones Flyway V1–V7
│   └── docs/                 Documentación de historias de usuario
├── frontend/                 SPA Angular 20 servida por Nginx
├── audit-service/            Microservicio de auditoría
│   ├── backend/              Consumer de Kafka + API REST (Spring Boot)
│   └── frontend/             Dashboard Angular + Tailwind
├── ops/
│   ├── prometheus/           Configuración de scraping
│   └── grafana/              Provisioning de datasources
├── docs/                     Arquitectura, API, ADRs, Kafka, CI/CD, backlog…
├── scripts/                  Smoke test y pruebas E2E
├── .github/workflows/        CI (build/test/Docker) y Qodana
├── docker-compose.yml        Orquestación del stack completo
├── Makefile                  Atajos de desarrollo
└── .env.example              Plantilla de variables de entorno
```

---

##  Inicio rápido

### Requisitos

- [Git](https://git-scm.com/)
- [Docker](https://docs.docker.com/get-docker/) con **Docker Compose v2**

```bash
docker --version && docker compose version && git --version
```

### 1. Clonar el repositorio

```bash
git clone <URL_DEL_REPOSITORIO>
cd SkillBridge-AI-JAVA-Angular
```

### 2. Configurar variables de entorno

```bash
cp .env.example .env
```

Edita `.env` y, como mínimo, define un secreto JWT propio y (opcionalmente) tu clave de Gemini:

```env
JWT_SECRET=<cadena-aleatoria-de-al-menos-32-caracteres>
GEMINI_API_KEY=<tu-api-key>      # opcional: sin ella todo funciona excepto la IA
```

Puedes generar un secreto seguro con:

```bash
openssl rand -base64 48
```

### 3. Levantar el stack

```bash
docker compose up --build -d
# o, con Make:
make up
```

### 4. Verificar

```bash
docker compose ps                       # todos los servicios deben estar healthy
./scripts/smoke-test.sh                 # comprueba health y catálogo público
```

Abre **http://localhost:8088** y listo. 

> La primera construcción descarga imágenes y dependencias; puede tardar varios minutos. Kafka y RabbitMQ deben estar `healthy` antes de que arranque el backend.

---

##  Servicios y puertos

| Servicio                      | URL | Notas |
|-------------------------------|---|---|
| **Aplicación web**            | http://localhost:8088 | Angular + Nginx (proxy de `/api`) |
| **API backend**               | http://localhost:8080 | Acceso directo |
| **Swagger UI**                | http://localhost:8080/swagger-ui.html | Documentación interactiva |
| **Health**                    | http://localhost:8080/actuator/health | Liveness: `/actuator/health/liveness` |
| **Dashboard de auditoría**    | http://localhost:8090 | Frontend del audit-service |
| **API de auditoría**          | http://localhost:8081/api/audit/events | |
| **RabbitMQ Management**       | http://localhost:15672 | `guest` / `guest` |
| **Mailpit (correos de prueba)** | http://localhost:8025 | Bandeja SMTP de desarrollo |
| **PostgreSQL**                | `localhost:5433` | Usuario/BD: `skillbridge` |
| **Kafka** (host)            | `localhost:9092` | Interno: `kafka:29092` |
| **Prometheus**              | http://localhost:9090 | Solo con profile `observability` |
| **Grafana**                 | http://localhost:3000 | `admin` / `admin` · profile `observability` |

> Los puertos de infraestructura se publican únicamente en `127.0.0.1` por seguridad. Los frontends (`8088` y `8090`) se publican en todas las interfaces.

---

##  Usuarios de prueba

Las migraciones de Flyway crean tres cuentas para explorar cada rol (contraseña común: `12345678`):

| Rol | Email | Contraseña |
|---|---|---|
| **ADMIN** | `admin@gmail.com` | `12345678` |
| **PROVIDER** | `provider@gmail.com` | `12345678` |
| **CUSTOMER** | `user@gmail.com` | `12345678` |

Además, al iniciar la aplicación se crea (si no existe) un usuario administrador `admin` / `12345678`.

>  **Solo para desarrollo.** Elimina o cambia estas credenciales antes de cualquier despliegue público (ver [Seguridad](#-seguridad)).

Los nuevos registros desde `/api/auth/register` se crean siempre con rol `CUSTOMER`.

---

##  Configuración

Toda la configuración se inyecta mediante variables de entorno. Docker Compose lee el archivo `.env` de la raíz.

### Backend principal

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/skillbridge` | URL JDBC de PostgreSQL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `skillbridge` / `skillbridge_dev` | Credenciales de BD |
| `DB_POOL_SIZE` | `5` | Tamaño del pool Hikari |
| `REDIS_URL` | `redis://localhost:6379` | Conexión a Redis (usa `rediss://` con TLS) |
| `RABBITMQ_URL` | `amqp://guest:guest@localhost:5672` | Conexión a RabbitMQ (`amqps://` en cloud) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers de Kafka |
| `KAFKA_TOPIC_BOOKING_EVENTS` | `booking-events` | Topic de eventos de negocio |
| `KAFKA_CONSUMER_GROUP` | `skillbridge-audit-group` | Consumer group |
| `JWT_SECRET` | *(valor de ejemplo)* | **Obligatorio cambiarlo.** Mínimo 32 caracteres |
| `JWT_EXPIRATION_MINUTES` | `120` | Vigencia del token |
| `JWT_COOKIE_NAME` | `access_token` | Nombre de la cookie de sesión |
| `JWT_COOKIE_SECURE` | `true` | Cookie solo por HTTPS |
| `JWT_COOKIE_SAME_SITE` | `None` | Política SameSite |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8088,http://localhost:4200` | Orígenes permitidos (separados por coma) |
| `OFFERINGS_CACHE_TTL_MINUTES` | `10` | TTL del catálogo en Redis |
| `BOOKING_CANCELLATION_MINIMUM_NOTICE_HOURS` | `24` | Anticipación mínima para cancelar |
| `NOTIFICATIONS_SENDER` | `simulated` | `simulated` o `email` |
| `NOTIFICATIONS_EMAIL_FROM` | `no-reply@skillbridge.local` | Remitente de correos |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | Servidor SMTP (Mailpit en Docker) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | *(vacío)* | Credenciales SMTP |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `false` | Opciones SMTP |

### Inteligencia artificial

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `AI_PROVIDER` | `gemini` | Proveedor de recomendaciones (`gemini` u `openai`) |
| `GEMINI_API_KEY` | *(vacío)* | Clave de Gemini (solo backend) |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Modelo multimodal |
| `OPENAI_API_KEY` | *(vacío)* | Clave de OpenAI (si se usa ese proveedor) |
| `AI_TIMEOUT_SECONDS` | `10` | Timeout de llamadas al proveedor |
| `AI_MEDIA_MAX_AUDIO_BYTES` | `10485760` | Límite de audio (10 MB) |
| `AI_MEDIA_MAX_IMAGE_BYTES` | `5242880` | Límite de imagen (5 MB) |
| `AI_MULTIPART_MAX_FILE_SIZE` / `AI_MULTIPART_MAX_REQUEST_SIZE` | `10MB` | Límites multipart de Spring |

### Frontend

| Variable | Descripción |
|---|---|
| `API_URL` | URL base de la API (por defecto `/api`). Se inyecta en build vía `scripts/generate-env.mjs` → `env.js` |
| `BACKEND_URL` | (Imagen Nginx) Upstream del backend. Por defecto `http://backend:8080` |

> **Nunca** subas `.env` al repositorio (ya está en `.gitignore`) ni expongas secretos en el código Angular.

---

##  API REST

Documentación interactiva completa en **Swagger UI**: `http://localhost:8080/swagger-ui.html`.

### Autenticación

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `POST` | `/api/auth/register` | Público | Registra un `CUSTOMER` y abre sesión |
| `POST` | `/api/auth/login` | Público | Inicia sesión (cookie `HttpOnly`) |
| `POST` | `/api/auth/logout` | Público | Cierra sesión |
| `GET` | `/api/auth/me` | Autenticado | Usuario y rol actuales |

### Catálogo y categorías

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/api/offerings` | Público | Servicios activos (caché Redis) |
| `GET` | `/api/offerings/me` | `PROVIDER` | Servicios del proveedor autenticado |
| `POST` | `/api/offerings` | `PROVIDER`, `ADMIN` | Crea un servicio |
| `PUT` | `/api/offerings/{id}` | `PROVIDER` (dueño), `ADMIN` | Actualiza un servicio |
| `POST` | `/api/offerings/{id}/activate` | `PROVIDER` (dueño), `ADMIN` | Reactiva un servicio |
| `POST` | `/api/offerings/{id}/deactivate` | `PROVIDER` (dueño), `ADMIN` | Desactiva un servicio |
| `GET` | `/api/admin/offerings` | `ADMIN` | Todos los servicios |
| `GET` | `/api/categorias` | Autenticado | Categorías activas |
| `POST` | `/api/categorias` | `PROVIDER`, `ADMIN` | Crea una categoría |

### Reservas y pagos

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `POST` | `/api/bookings` | Autenticado + `Idempotency-Key` | Crea una reserva (idempotente) |
| `GET` | `/api/bookings/me` | Autenticado | Reservas del usuario |
| `PATCH` | `/api/bookings/{id}/cancel` | Autenticado (dueño) | Cancela una reserva |
| `POST` | `/api/payments/authorize` | Autenticado + `Idempotency-Key` | Autoriza un pago simulado |

### Inteligencia artificial

| Método | Ruta | Cuerpo |
|---|---|---|
| `POST` | `/api/ai/recommendations` | JSON `{ "goal": "..." }` |
| `POST` | `/api/ai/recommendations/voice` | `multipart/form-data`, campo `file` (audio) |
| `POST` | `/api/ai/recommendations/image` | `multipart/form-data`, campo `file` (imagen) |

### Administración de usuarios (`ADMIN`)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/admin/users?page=0&size=20&sort=createdAt,desc` | Listado paginado (orden: `name`, `email`, `role`, `status`, `createdAt`) |
| `GET` | `/api/admin/users/{id}` | Detalle |
| `PUT` | `/api/admin/users/{id}/status` | `{"status":"ACTIVE\|SUSPENDED"}` |
| `PUT` | `/api/admin/users/{id}/role` | `{"role":"CUSTOMER\|PROVIDER\|ADMIN"}` |

### Ejemplos con `curl`

```bash
# Catálogo público
curl http://localhost:8080/api/offerings

# Registro con Bearer (devuelve cookie de sesión; usa -c/-b para persistirla)
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@gmail.com","password":"12345678"}'

# Crear una reserva idempotente
curl -X POST http://localhost:8080/api/bookings \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7' \
  -H "X-XSRF-TOKEN: $(grep XSRF-TOKEN cookies.txt | awk '{print $7}')" \
  -d '{"offeringId":"11111111-1111-1111-1111-111111111111","scheduledAt":"2030-10-10T15:00:00Z"}'
```

### Códigos de respuesta y errores

Los errores siguen el formato **ProblemDetail (RFC 7807/9457)**:

```json
{
  "type": "about:blank",
  "title": "Business rule violation",
  "status": 422,
  "detail": "La reserva debe programarse en una fecha futura"
}
```

| Código | Significado |
|---|---|
| `400` | Datos inválidos o cabecera `Idempotency-Key` ausente |
| `401` | No autenticado / token inválido |
| `403` | Rol insuficiente o cuenta suspendida |
| `404` | Recurso inexistente (o ajeno, para no revelar su existencia) |
| `409` | `Idempotency-Key` reutilizada con un cuerpo distinto |
| `413` | Archivo multimedia demasiado grande |
| `422` | Regla de negocio violada |
| `503` | Proveedor de IA no disponible |

---

##  Flujos clave

### Consulta de catálogo (Cache-Aside)

```mermaid
flowchart TD
    A[GET /api/offerings] --> B{¿En Redis?}
    B -- HIT --> D[Respuesta]
    B -- MISS --> C[(PostgreSQL)] --> E[Guardar en Redis<br/>TTL configurable] --> D
```

Las operaciones de escritura sobre servicios invalidan la caché a través de `OfferingCachePort`.

### Creación de reserva
![Reservas](images/Reservar.png)


```mermaid
sequenceDiagram
    participant C as Cliente (Angular)
    participant API as Spring Boot
    participant DB as PostgreSQL
    participant RMQ as RabbitMQ
    participant K as Kafka
    participant N as Notification Consumer

    C->>API: POST /api/bookings (Idempotency-Key)
    API->>API: Valida reglas y clave de idempotencia
    API->>DB: Guarda la reserva
    API->>RMQ: Publica notificación
    API->>K: Publica BookingCreated
    API-->>C: 201 Created
    RMQ->>N: Entrega asíncrona (retry + DLQ)
```

### Máquina de estados de una reserva

![Reservas](images/reservas.png)

| Estado inicial | Acción | Resultado |
|---|---|---|
| `CREATED` | Cancelar (≥ 24 h antes) | `CANCELLED` + historial + notificación + evento |
| `CANCELLED` | Cancelar de nuevo | `200 OK` idempotente, sin efectos adicionales |
| `CONFIRMED` / `COMPLETED` | Cancelar | `422` (regla de negocio) |

La cancelación usa **bloqueo pesimista** de escritura más **versionado optimista** para garantizar una única transición bajo concurrencia.

### Pago simulado

![img.png](img.png)
`POST /api/payments/authorize` exige `Idempotency-Key`. El adaptador simulado es determinista:

| Condición | Resultado |
|---|---|
| Tarjeta terminada en `0000` | `DECLINED` |
| CVV = `TIMEOUT` | `DECLINED` tras 2 s (timeout simulado) |
| CVV = `ERROR` | Error del sistema de pagos |
| Cualquier otro caso | `APPROVED` |

El resultado se publica en el exchange `payment.events` y un consumer actualiza la reserva.

---

##  Mensajería: RabbitMQ y Kafka

El proyecto usa **ambos** brokers con responsabilidades distintas:

| | **RabbitMQ** | **Apache Kafka** |
|---|---|---|
| **Propósito** | Tareas asíncronas y notificaciones | Eventos de negocio persistentes y auditoría |
| **Naturaleza** | Mensajes transitorios (se eliminan al consumirse) | Log de eventos que múltiples consumidores pueden releer |
| **Uso aquí** | Notificaciones de reserva, resultados de pago | `BookingCreated`, `BookingCancelled`, `UserLoggedIn`, `UserRegistered`, eventos de ofertas y recomendaciones |

### Topología RabbitMQ

| Elemento | Nombre |
|---|---|
| Exchange de notificaciones | `notification.exchange` (topic) |
| Claves de enrutamiento | `notification.booking.created`, `notification.booking.cancelled` |
| Cola principal | `notification.queue` |
| Dead Letter Exchange / Queue | `notification.dlx` → `notification.dlq` |
| Exchanges de eventos | `booking.events`, `payment.events` |

**Resiliencia:** ACK automático, hasta **3 reintentos** con backoff exponencial (2 s → 10 s) y, al agotarlos, el mensaje va a la **DLQ**. Los *publisher confirms* y *returns* están activos.

### Envelope de eventos de Kafka

Todos los eventos comparten el contenedor `BusinessEvent`:

```json
{
  "eventId": "uuid",
  "eventType": "BookingCreated",
  "aggregateId": "uuid",
  "aggregateType": "Booking",
  "occurredAt": "2030-10-10T15:00:00Z",
  "correlationId": "uuid",
  "version": 1,
  "actorUserId": "uuid",
  "actorUsername": "user@gmail.com",
  "actorRole": "CUSTOMER",
  "action": "CREATE",
  "resource": "BOOKING",
  "resourceId": "uuid",
  "payload": { }
}
```

Más detalle en [`docs/kafka.md`](docs/kafka.md) y [`docs/hu-12-notificaciones-rabbitmq.md`](docs/hu-12-notificaciones-rabbitmq.md).

---

##  Servicio de auditoría

Microservicio independiente (`audit-service/`) que consume el topic `booking-events` de Kafka y expone una API de consulta junto a un dashboard.

| Endpoint | Descripción |
|---|---|
| `GET /api/audit/events` | Últimos eventos registrados |
| `GET /api/audit/events/{eventId}` | Detalle de un evento |
| `GET /api/audit/stats` | Estadísticas agregadas |

**Dashboard (http://localhost:8090):** Angular 20 con Signals y Tailwind 4, sondeo cada 10 s, filtros por tipo/acción/rol/recurso, búsqueda por ID o usuario e inspección del payload JSON.

Más información en [`docs/audit-service.md`](docs/audit-service.md).

---

##  Inteligencia artificial

![IA](images/IA.png)

El asistente recomienda **hasta 3 servicios del catálogo activo** con puntaje y justificación. Todo el procesamiento ocurre en el backend: **la API key nunca llega al navegador**.

### Entradas soportadas

| Modalidad | Endpoint | Formatos | Límite |
|----------|---|---|---|
|  Texto  | `/api/ai/recommendations` | JSON | — |
|  Voz   | `/api/ai/recommendations/voice` | WAV, MP3, M4A, MP4, WEBM | 10 MB |
|  Imagen | `/api/ai/recommendations/image` | JPEG, PNG, WEBP | 5 MB |

- Los archivos se procesan **en memoria** y no se almacenan.
- La voz se transcribe antes de recomendar (Gemini por defecto; Groq opcional con `app.ai.audio-provider=groq`).
- El resultado incluye `recommendationId`, `inputType`, explicación y la lista de recomendaciones.
- Los identificadores inválidos que "alucine" el modelo se descartan.
- Se publican los eventos `RecommendationRequested` y `RecommendationGenerated` con `Correlation ID`.

### Privacidad

El prompt solo contiene el objetivo del usuario y el catálogo público. No se envían correos, contraseñas ni tokens.

### Proveedores

La abstracción `AiRecommendationPort` permite cambiar de proveedor por configuración (`AI_PROVIDER`). Gemini es el proveedor activo por defecto; consulta [`docs/ADR-001-AI-PROVIDER-ABSTRACTION.md`](docs/ADR-001-AI-PROVIDER-ABSTRACTION.md) y [`docs/AI_PROVIDER_COMPARISON.md`](docs/AI_PROVIDER_COMPARISON.md) para la comparativa y las decisiones de diseño.

---

##  Seguridad

| Medida | Implementación |
|---|---|
| **Autenticación** | JWT firmado, entregado en cookie `HttpOnly` (también se acepta `Authorization: Bearer`) |
| **Sesión** | API *stateless* (`SessionCreationPolicy.STATELESS`) |
| **CSRF** | Token en cookie (`X-XSRF-TOKEN`); se exige solo en peticiones mutables autenticadas por cookie |
| **Autorización** | RBAC (`CUSTOMER`, `PROVIDER`, `ADMIN`) con `@PreAuthorize` y políticas de dominio por recurso (propiedad de ofertas/reservas) |
| **Contraseñas** | BCrypt |
| **CORS** | Orígenes explícitos y configurables; credenciales habilitadas |
| **Cuentas suspendidas** | No pueden iniciar sesión y su token deja de ser válido de inmediato |
| **Idempotencia** | `Idempotency-Key` en reservas y pagos; índice único parcial en BD |
| **Trazabilidad** | `X-Correlation-Id` en logs (MDC) y eventos Kafka |
| **Datos sensibles** | Respuestas sin contraseña/hash; logs de auditoría sin correos, contraseñas ni JWT |
| **Contenedores** | Imagen del backend con usuario no-root; cabeceras de seguridad en Nginx |

###  Checklist antes de publicar en producción

- [ ] Cambiar `JWT_SECRET` por un valor aleatorio de 32+ caracteres.
- [ ] **Eliminar o rotar los usuarios semilla** (`admin@gmail.com`, `provider@gmail.com`, `user@gmail.com`) y el administrador `admin` creado al arrancar (`AdminSeeder`).
- [ ] Cambiar las credenciales de PostgreSQL, RabbitMQ (`guest/guest`) y Grafana (`admin/admin`).
- [ ] Restringir `/actuator/prometheus` (hoy es público para facilitar el scraping local).
- [ ] Revisar `JWT_COOKIE_SECURE` y `JWT_COOKIE_SAME_SITE` según el dominio y HTTPS.
- [ ] Añadir autenticación al **audit-service** (su API no incluye Spring Security).
- [ ] No versionar `.env` ni claves de proveedores; rotar cualquier clave que se haya compartido.

---

##  Observabilidad

```bash
docker compose --profile observability up --build -d
# o
make observability
```

| Herramienta | URL | Credenciales |
|---|---|---|
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |

Prometheus consulta `backend:8080/actuator/prometheus` cada 15 s. Métricas útiles:

```text
http_server_requests_seconds_count
jvm_memory_used_bytes
process_cpu_usage
```

Endpoints de Actuator expuestos: `health`, `info`, `metrics`, `prometheus`. El `healthcheck` de Docker usa `/actuator/health/liveness`.

---

##  Pruebas

### Backend

```bash
cd backend
mvn clean verify          # unitarias + integración + informe JaCoCo
mvn test                  # solo unitarias (rápido, sin Docker para la mayoría)
```

- Informe de cobertura: `backend/target/site/jacoco/index.html`.
- Las pruebas `*IT` (por ejemplo `NotificationFlowIT`, `BookingCancellationIT`) y varias de persistencia usan **Testcontainers** (PostgreSQL y RabbitMQ reales) y requieren **Docker en ejecución**. Se ejecutan con Failsafe durante `verify`.
- Se incluyen pruebas de dominio, servicios, adaptadores, seguridad HTTP, filtros, validación de archivos multimedia, adaptadores de IA, pagos, notificaciones y políticas de acceso.

### Frontend

```bash
cd frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
```

### Smoke y E2E

```bash
./scripts/smoke-test.sh http://localhost:8080      # health + catálogo
# Windows (PowerShell):
./scripts/e2e-offerings.ps1
./scripts/e2e-admin-users.ps1
```

---

##  CI/CD

El workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) se ejecuta en cada *push* y *pull request* hacia `main` y `develop`:

| Job | Acciones |
|---|---|
| **backend** | Java 21 (Temurin) → `mvn clean verify` |
| **frontend** | Node 22 → `npm ci` → tests (ChromeHeadless) → `npm run build` |
| **docker** | Construye las imágenes de backend y frontend (sin publicar) |

Adicionalmente hay un workflow de **Qodana** para calidad de código. Consulta [`docs/CI-CD.md`](docs/CI-CD.md) para las políticas de protección de ramas.

**Flujo de ramas sugerido:** `feature/*` → Pull Request → CI + revisión → `develop` → `main`.

---

##  Despliegue

El proyecto está pensado para dos topologías:

### A. Contenedores (Docker Compose / cualquier plataforma de contenedores)

`docker compose up --build -d` levanta el stack completo con Nginx sirviendo Angular y haciendo de *reverse proxy* de `/api`.

### B. Cloud gratuito para demos

| Componente | Servicio sugerido |
|---|---|
| Frontend Angular | Vercel (`frontend/vercel.json`, variable `API_URL`) |
| Backend Spring Boot | Render (Docker, *Root Directory* `backend`) |
| PostgreSQL | Neon |
| Redis | Upstash |
| RabbitMQ | CloudAMQP |
| IA | Gemini Developer API |

Pasos esenciales:

1. **Backend (Render):** define `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_URL`, `RABBITMQ_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `JWT_SECRET`, `GEMINI_API_KEY`, `CORS_ALLOWED_ORIGINS` y configura el health check en `/actuator/health/liveness`.
2. **Frontend (Vercel):** *Root Directory* `frontend` y variable `API_URL=https://<tu-backend>/api`.
3. **CORS:** añade el dominio del frontend a `CORS_ALLOWED_ORIGINS`.

> El backend requiere un broker **Kafka** accesible. En el modelo cloud gratuito debes aprovisionar uno gestionado (o desactivar/ajustar esa integración) además de los demás servicios.
>
> Los planes gratuitos cambian con el tiempo y los servicios en suspensión sufren *cold starts*; verifica siempre las condiciones vigentes. Consulta [`docs/DEPLOYMENT-CHECKLIST.md`](docs/DEPLOYMENT-CHECKLIST.md).

---

##  Desarrollo local sin Docker

Útil para depurar. Necesitas **JDK 21**, **Maven 3.9+**, **Node 22** y las dependencias (PostgreSQL, Redis, RabbitMQ y Kafka) en ejecución — puedes levantarlas solo con Compose:

```bash
docker compose up -d postgres redis rabbitmq kafka mailpit
```

Como PostgreSQL se publica en el puerto `5433` del host:

```bash
# Backend
cd backend
export DATABASE_URL=jdbc:postgresql://localhost:5433/skillbridge
export DATABASE_USER=skillbridge DATABASE_PASSWORD=skillbridge_dev
export JWT_COOKIE_SECURE=false JWT_COOKIE_SAME_SITE=Lax
mvn spring-boot:run

# Frontend (en otra terminal; proxy de /api hacia :8080)
cd frontend
npm install
npm start          # http://localhost:4200
```

> Con HTTP plano en local, desactiva `JWT_COOKIE_SECURE`; de lo contrario el navegador descartará la cookie de sesión.

### Comandos útiles (Makefile)

| Comando | Acción |
|---|---|
| `make up` | Construye y levanta el stack en segundo plano |
| `make down` | Detiene los contenedores |
| `make logs` | Sigue los logs |
| `make ps` | Estado de los servicios |
| `make observability` | Levanta con Prometheus y Grafana |
| `make build` | Construye las imágenes |

Otros comandos frecuentes:

```bash
docker compose logs -f backend                                   # logs del backend
docker compose exec postgres psql -U skillbridge -d skillbridge  # consola SQL
docker compose exec redis redis-cli                              # consola Redis
docker compose up -d --build backend                             # recrear solo el backend
docker compose down -v                                           # detener y borrar datos
```

---

##     Solución de problemas

| Síntoma | Causa probable y solución |
|---|---|
| `port is already allocated` | Otro proceso usa el puerto. Libéralo o cambia el mapeo en `docker-compose.yml`. |
| El backend no arranca / espera | Depende de PostgreSQL, Redis, RabbitMQ y Kafka *healthy*. Revisa `docker compose ps` y los logs. |
| Dentro de Docker no conecta a servicios | Usa los nombres de servicio (`postgres:5432`, `redis:6379`, `rabbitmq:5672`, `kafka:29092`), no `localhost`. |
| La IA responde que falta la API key | Define `GEMINI_API_KEY` en `.env` y reinicia: `docker compose up -d --build backend`. |
| Error de CORS en el navegador | Añade el origen exacto a `CORS_ALLOWED_ORIGINS`. No uses `*` con credenciales. |
| Sesión que no se mantiene en local | La cookie es `Secure`; en HTTP define `JWT_COOKIE_SECURE=false` (y `JWT_COOKIE_SAME_SITE=Lax`). |
| `POST` devuelve `403` por CSRF | Envía la cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN` (el frontend lo hace automáticamente). |
| Testcontainers falla (`client version … too old`) | Actualiza Docker / Testcontainers (el proyecto usa `1.21.4`) y comprueba que el usuario tenga acceso a Docker. |
| Los correos no aparecen | Define `NOTIFICATIONS_SENDER=email` y revisa Mailpit en http://localhost:8025. |

---

##  Limitaciones conocidas y roadmap

### Limitaciones actuales (transparencia técnica)

- **Sin Transactional Outbox:** si RabbitMQ o Kafka fallan tras confirmar la transacción en PostgreSQL, el evento puede perderse. Es un riesgo asumido y documentado.
- **DLQ sin reproceso automático:** los mensajes fallidos se inspeccionan manualmente.
- **Pagos simulados:** la pasarela es determinista y el repositorio de resultados de pago es **en memoria**.
- **Audit-service en memoria:** conserva solo los últimos 500 eventos y se reinicia con el servicio; su API no tiene autenticación en el backend.
- **Pruebas de integración con Docker:** requieren Docker disponible para ejecutarse.

### Próximos pasos sugeridos

- [ ] Implementar **Transactional Outbox** y consumidores idempotentes.
- [ ] Persistir el servicio de auditoría (PostgreSQL / almacenamiento durable) y asegurar su API.
- [ ] Integrar una pasarela de pago real y persistir sus resultados.
- [ ] Disponibilidad y calendario de proveedores.
- [ ] Circuit breaker y *retry* con Resilience4j para proveedores de IA.
- [ ] Trazado distribuido con OpenTelemetry y *structured logging*.
- [ ] Rate limiting y protección contra fuerza bruta en el login.
- [ ] Despliegue en Kubernetes (Helm, HPA, Ingress).

---

##  Documentación adicional

| Documento | Contenido |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Estilo arquitectónico y razones de cada tecnología |
| [`docs/API.md`](docs/API.md) | Referencia de endpoints |
| [`docs/kafka.md`](docs/kafka.md) | Integración con Kafka y catálogo de eventos |
| [`docs/audit-service.md`](docs/audit-service.md) | Microservicio de auditoría |
| [`docs/hu-12-notificaciones-rabbitmq.md`](docs/hu-12-notificaciones-rabbitmq.md) | Notificaciones asíncronas, retry y DLQ |
| [`docs/hu-17-idempotencia-correlation-id.md`](docs/hu-17-idempotencia-correlation-id.md) | Idempotencia y Correlation ID |
| [`docs/hu-23-recomendaciones-multimodales.md`](docs/hu-23-recomendaciones-multimodales.md) | Recomendaciones por texto, voz e imagen |
| [`docs/cancelacion-reserva-propia-backend.md`](docs/cancelacion-reserva-propia-backend.md) | Cancelación de reservas y concurrencia |
| [`docs/ADR-001-AI-PROVIDER-ABSTRACTION.md`](docs/ADR-001-AI-PROVIDER-ABSTRACTION.md) | Decisión de abstracción de proveedores de IA |
| [`docs/adr/`](docs/adr) | Registros de decisiones arquitectónicas (monolito modular, servicios gestionados) |
| [`docs/CI-CD.md`](docs/CI-CD.md) | Pipeline y protección de ramas |
| [`docs/DEPLOYMENT-CHECKLIST.md`](docs/DEPLOYMENT-CHECKLIST.md) | Lista de verificación de despliegue |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Historias de usuario y backlog |

---

##  Contribución

1. Crea una rama desde `develop`: `feature/<nombre>`.
2. Respeta la regla de dependencias de la arquitectura hexagonal (el dominio y la aplicación no importan infraestructura).
3. Incluye pruebas relevantes y no subas secretos.
4. Abre un Pull Request usando la [plantilla](.github/PULL_REQUEST_TEMPLATE.md) y espera a que el CI esté en verde.

**Definición de terminado:** criterios funcionales cumplidos, validaciones y manejo de errores, pruebas, CI en verde, OpenAPI actualizado, logs útiles sin datos sensibles y revisión por otro integrante.

---

##  Licencia

Distribuido bajo la licencia **MIT**. Consulta el archivo [`LICENSE`](LICENSE) para más información.

<div align="center">

Hecho con Java, Angular y mucha mensajería asíncrona.

</div>



