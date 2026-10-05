# SkillBridge AI

> Proyecto integrador profesional para practicar **Java 21 + Spring Boot + Angular + Arquitectura Hexagonal + PostgreSQL + Redis + RabbitMQ + Nginx + IA + observabilidad + Docker + CI/CD + despliegue cloud**.

SkillBridge AI es una plataforma de mentorías y servicios profesionales. Los proveedores pueden publicar servicios y los usuarios pueden descubrirlos, autenticarse, reservar una sesión y pedir a un asistente de IA recomendaciones basadas únicamente en el catálogo existente.

El objetivo principal no es el dominio de negocio: el dominio fue escogido para que cada tecnología tenga un **caso de uso real** y para que el proyecto pueda crecer hacia una solución distribuida más compleja.

---

## 1. Qué van a aprender

Este repositorio sirve como punto de partida para trabajar:

- Arquitectura Hexagonal / Ports & Adapters.
- DDD táctico básico y separación de responsabilidades.
- Principios SOLID.
- APIs REST con Spring Boot.
- Spring Security + JWT + RBAC.
- Persistencia con Spring Data JPA y PostgreSQL.
- Migraciones con Flyway.
- Cache-Aside con Redis.
- Eventos asíncronos con RabbitMQ.
- Retry + Dead Letter Queue.
- Integración de IA desde Java sin exponer API keys en Angular.
- Angular standalone.
- Nginx como servidor de estáticos y reverse proxy.
- Dockerfiles multi-stage.
- Docker Compose para levantar todo el entorno.
- Health checks.
- Spring Boot Actuator + Micrometer.
- Prometheus + Grafana.
- JUnit + Mockito + JaCoCo.
- CI con GitHub Actions.
- Despliegue en plataformas gratuitas para laboratorio.

---

## 2. Caso de uso

### SkillBridge AI

Una persona puede registrarse y buscar servicios como:

- mentoría Java Backend;
- mentoría Angular;
- arquitectura de software;
- DevOps;
- cloud;
- preparación de entrevistas;
- diseño UX;
- entrenamiento personalizado;
- consultoría especializada.

El flujo inicial implementado es:

```text
Usuario
  |
  v
Angular
  |
  v
Nginx
  |
  v
Spring Boot
  |
  +----------> PostgreSQL
  |
  +----------> Redis
  |
  +----------> RabbitMQ
  |
  +----------> Gemini API
```

### Flujo: consultar catálogo

```text
GET /api/offerings
       |
       v
OfferingService
       |
       v
     Redis
    /     \
  HIT     MISS
   |        |
   |        v
   |    PostgreSQL
   |        |
   |        v
   |    guardar Redis
   |        |
   +--------+
       |
       v
    Response
```

Este flujo permite explicar **Cache Aside**, TTL, cache hit, cache miss e invalidación.

### Flujo: crear reserva

```text
POST /api/bookings
        |
        v
CreateBookingUseCase
        |
        +------ validar reglas de negocio
        |
        +------ guardar Booking en PostgreSQL
        |
        +------ publicar BookingCreated
                         |
                         v
                      RabbitMQ
                         |
                         v
                booking.created.queue
                         |
                         v
              Notification Consumer
```

El consumidor actual registra la notificación en logs. Las células deben extenderlo con email, auditoría, WhatsApp, otro microservicio o persistencia de notificaciones.

### Flujo: IA

```text
Angular
   |
POST /api/ai/recommendations
   |
   v
GenerateRecommendationUseCase
   |
   v
AiRecommendationPort
   |
   v
GeminiAiAdapter
   |
   v
Gemini API
```

**Angular jamás recibe la API key de Gemini.**

Además, el prompt solo envía el objetivo escrito por el usuario y el catálogo público. No se envían email, contraseña, JWT ni información privada del usuario.

---

# 3. Arquitectura general

```text
                                  INTERNET
                                      |
                                      v
                            +-------------------+
                            | NGINX / Vercel CDN|
                            +---------+---------+
                                      |
                     +----------------+----------------+
                     |                                 |
                     v                                 v
                  Angular                           /api/**
                                                       |
                                                       v
                                             +------------------+
                                             |   Spring Boot    |
                                             |------------------|
                                             | Hexagonal        |
                                             | Security + JWT   |
                                             | Validation       |
                                             | RFC 7807         |
                                             +--------+---------+
                                                      |
                   +----------------------------------+-------------------------------+
                   |                  |                    |                          |
                   v                  v                    v                          v
              PostgreSQL           Redis               RabbitMQ                   Gemini
              System of            Cache               Events                     AI
               Record
                                                          |
                                                          v
                                                   Async Consumers

                         Observability

Spring Boot --> Actuator --> Micrometer --> Prometheus --> Grafana
```

---

# 4. Arquitectura Hexagonal del backend

```text
backend/src/main/java/com/riwi/skillbridge
|
+-- domain
|   +-- model
|   +-- exception
|
+-- application
|   +-- port
|   |   +-- in
|   |   +-- out
|   +-- service
|
+-- infrastructure
    +-- adapter
    |   +-- in
    |   |   +-- rest
    |   |   +-- messaging
    |   +-- out
    |       +-- persistence
    |       +-- cache
    |       +-- messaging
    |       +-- ai
    +-- config
    +-- security
```

## Regla de dependencia

El dominio no debería saber que existen:

- PostgreSQL;
- JPA;
- Redis;
- RabbitMQ;
- Gemini;
- HTTP;
- Angular;
- Nginx.

Ejemplo:

```java
public interface AiRecommendationPort {
    String recommend(String goal, List<Offering> offerings);
}
```

La aplicación depende de ese contrato.

La infraestructura aporta una implementación:

```text
AiRecommendationPort
        ^
        |
GeminiAiAdapter
```

Esto permite reemplazar Gemini por otro proveedor sin modificar el caso de uso.

---

# 5. Tecnologías

## Backend

- Java 21
- Spring Boot 3.5.x
- Spring MVC
- Spring Security
- Spring Data JPA
- Spring Data Redis
- Spring AMQP
- Bean Validation
- Flyway
- PostgreSQL
- JWT
- Spring Boot Actuator
- Micrometer Prometheus
- Springdoc OpenAPI
- JUnit 5
- Mockito
- Testcontainers preparado como dependencia
- JaCoCo

## Frontend

- Angular 20
- Standalone Components
- HttpClient
- Functional Interceptor
- Angular Router
- Runtime configuration mediante `env.js`
- Nginx para la imagen Docker de producción

## Infraestructura

- Docker
- Docker Compose
- PostgreSQL 17
- Redis 8
- RabbitMQ 4 Management
- Nginx
- Prometheus
- Grafana

## Cloud gratuito sugerido para laboratorio

```text
Frontend       -> Vercel Hobby
Backend        -> Render Free Web Service
PostgreSQL     -> Neon Free
Redis          -> Upstash Redis Free
RabbitMQ       -> CloudAMQP Little Lemur Free
IA             -> Gemini Developer API Free Tier
Repositorio    -> GitHub
CI             -> GitHub Actions
```

> Los planes gratuitos cambian con el tiempo. Verifica siempre las páginas oficiales antes de una cohorte nueva.

---

# 6. Requisitos locales

La ruta recomendada solo necesita:

- Git
- Docker Engine / Docker Desktop
- Docker Compose v2

**No es obligatorio instalar Java, Maven, Node, PostgreSQL, Redis ni RabbitMQ** para ejecutar el stack completo con Docker.

Comprueba:

```bash
docker --version
docker compose version
git --version
```

---

# 7. Ejecución local con Docker Compose

## Paso 1 — Clonar

```bash
git clone <URL_DEL_REPOSITORIO>
cd skillbridge-ai
```

## Paso 2 — Variables de entorno

```bash
cp .env.example .env
```

Edita `.env`.

Para ejecutar todo excepto IA puedes dejar:

```env
GEMINI_API_KEY=
```

Para probar IA debes agregar una API key válida.

La configuración recomendada para este proyecto es:

```env
GEMINI_MODEL=gemini-3.8-flash
```

La API key se utiliza únicamente en Spring Boot. Angular nunca se conecta
directamente con Gemini.

Cambia siempre el secreto JWT:

```env
JWT_SECRET=una-clave-larga-aleatoria-de-al-menos-32-caracteres
```

Puedes generar una:

```bash
openssl rand -base64 48
```

## Paso 3 — Levantar

```bash
docker compose up --build
```

O en segundo plano:

```bash
docker compose up --build -d
```

Con Make:

```bash
make up
```

## Paso 4 — Verificar

```bash
docker compose ps
```

Todos los servicios principales deberían estar `running`/`healthy`.

## URLs locales

| Servicio | URL |
|---|---|
| Aplicación Angular vía Nginx | http://localhost:8088 |
| Backend directo | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| RabbitMQ Management | http://localhost:15672 |
| PostgreSQL | localhost:5432 |

RabbitMQ local:

```text
user: guest
password: guest
```

---

# 8. Primer recorrido funcional

## 8.1 Catálogo público

```bash
curl http://localhost:8080/api/offerings
```

Haz la petición dos veces.

La primera puede llegar a PostgreSQL y guardar el resultado en Redis. La siguiente será candidata a resolverse desde Redis.

Para inspeccionar Redis:

```bash
docker compose exec redis redis-cli
```

Luego:

```redis
KEYS *
GET offerings:active
TTL offerings:active
```

## 8.2 Registrar usuario

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "name":"Ada Lovelace",
    "email":"ada@example.com",
    "password":"Password123"
  }'
```

Obtendrás:

```json
{
  "token": "...",
  "tokenType": "Bearer"
}
```

También puedes hacerlo desde la interfaz Angular.

## 8.3 Crear una reserva

Copia el JWT y ejecuta:

```bash
TOKEN="PEGA_EL_TOKEN"
```

Uno de los servicios iniciales tiene este id:

```text
11111111-1111-1111-1111-111111111111
```

Crea una reserva con una fecha futura:

```bash
curl -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "offeringId":"11111111-1111-1111-1111-111111111111",
    "scheduledAt":"2030-10-10T15:00:00Z"
  }'
```

Ahora entra a:

```text
http://localhost:15672
```

Busca:

```text
booking.created.queue
```

El consumer de Spring debería consumir el evento.

Logs:

```bash
docker compose logs -f backend
```

Deberías ver una línea similar a:

```text
Async booking notification -> bookingId=...
```

## 8.4 Probar recomendaciones con Gemini

La pantalla de IA requiere una sesión iniciada. Puedes registrarte desde:

```text
http://localhost:8088/login
```

Después entra en:

```text
http://localhost:8088/ai
```

Escribe un objetivo, por ejemplo:

```text
Quiero aprender arquitectura hexagonal con Java y Spring Boot
```

Pulsa **Pedir recomendación**. El backend consultará Gemini y devolverá hasta
tres servicios del catálogo relacionados con el objetivo.

También puedes probar el endpoint directamente con el JWT obtenido al
registrarte o iniciar sesión:

```bash
curl -X POST http://localhost:8080/api/ai/recommendations \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"goal":"Quiero aprender arquitectura hexagonal con Java"}'
```

Si Gemini no está configurado, la respuesta indicará que falta
`GEMINI_API_KEY`. Si el proveedor está temporalmente indisponible, el backend
devolverá un error controlado en lugar de dejar la petición esperando
indefinidamente.

---

# 9. RabbitMQ profesional: retry + DLQ

La cola principal está configurada con una Dead Letter Queue:

```text
booking.events
      |
      v
booking.created.queue
      |
   consumer
      |
   falla x3
      |
      v
 booking.dlx
      |
      v
booking.created.dlq
```

Los estudiantes pueden provocar una excepción temporal en el consumer y observar el comportamiento.

Conceptos a discutir:

- Producer.
- Consumer.
- Exchange.
- Routing Key.
- Queue.
- Acknowledgement.
- Retry.
- Dead Letter Queue.
- Message durability.
- Idempotency.
- Eventual consistency.

### Reto avanzado

Implementar **Transactional Outbox**.

El starter publica el evento después de persistir la reserva. En un sistema crítico existe una ventana de fallo entre la transacción de PostgreSQL y RabbitMQ. El patrón Outbox es la evolución recomendada para resolver ese problema.

---

# 10. IA

Crea una API key del proveedor y colócala únicamente en:

```env
GEMINI_API_KEY=...
```

Nunca hagas esto en Angular:

```typescript
const GEMINI_API_KEY = 'secret'; // NO
```

El navegador es un entorno no confiable: cualquier secreto incluido en el bundle frontend puede ser inspeccionado.

## Endpoint

```text
POST /api/ai/recommendations
```

Ejemplo:

```bash
curl -X POST http://localhost:8080/api/ai/recommendations \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "goal":"Quiero prepararme para una entrevista Java backend y mejorar arquitectura"
  }'
```

La IA solo recibe:

- objetivo textual;
- catálogo público disponible.

No recibe contraseña, JWT ni datos internos.

Para un entorno educativo evita introducir información confidencial o datos personales reales en prompts de servicios gratuitos.

---

# 11. Nginx

El frontend usa un Dockerfile multi-stage:

```text
Node 22
  |
  | npm run build
  v
Angular dist
  |
  v
Nginx
```

La imagen final no necesita Node. El upstream se parametriza con `BACKEND_URL` y la imagen oficial de Nginx genera su configuración al iniciar.

Nginx cumple dos funciones:

1. Servir el build estático de Angular.
2. Reverse proxy de `/api/*` hacia Spring Boot.

```text
http://localhost:8088/
       |
       v
     Nginx
     /   \
    /     \
Angular  /api/*
          |
          v
      backend:8080
```

Angular consume:

```text
/api
```

en lugar de acoplarse a `localhost:8080`.

Además `try_files` permite que Angular Router funcione al refrescar rutas del SPA.

---

# 12. Observabilidad

Los servicios de observabilidad están detrás de un profile para no consumir recursos siempre.

Levanta todo con:

```bash
docker compose --profile observability up --build -d
```

O:

```bash
make observability
```

URLs:

| Servicio | URL |
|---|---|
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

Grafana local:

```text
user: admin
password: admin
```

Prometheus consulta:

```text
http://backend:8080/actuator/prometheus
```

Prueba métricas como:

```text
http_server_requests_seconds_count
jvm_memory_used_bytes
process_cpu_usage
```

En Grafana crea un dashboard con:

- requests por endpoint;
- latencia;
- errores 4xx/5xx;
- memoria JVM;
- CPU;
- threads.

---

# 13. Ejecutar backend sin Docker

Requisitos:

- JDK 21
- Maven 3.9+
- PostgreSQL
- Redis
- RabbitMQ

Ejemplo:

```bash
cd backend
mvn spring-boot:run
```

Pero para el laboratorio se recomienda Docker Compose para evitar diferencias de configuración entre equipos.

---

# 14. Ejecutar Angular sin Docker

```bash
cd frontend
npm install
npm start
```

Abre:

```text
http://localhost:4200
```

El `proxy.conf.json` envía `/api` hacia:

```text
http://localhost:8080
```

---

# 15. Tests

## Backend

```bash
cd backend
mvn clean verify
```

El reporte JaCoCo queda en:

```text
backend/target/site/jacoco/index.html
```

El proyecto incluye un ejemplo de prueba de `OfferingService` que demuestra que cuando existe cache hit el repositorio no debe consultarse.

## Siguiente nivel recomendado

Agregar Testcontainers para probar:

- PostgreSQL real.
- Flyway migrations.
- repositorios JPA.
- Redis.
- RabbitMQ.

---

# 16. CI con GitHub Actions

Existe:

```text
.github/workflows/ci.yml
```

En cada Pull Request ejecuta:

```text
Backend
  -> Java 21
  -> mvn clean verify

Frontend
  -> Node 22
  -> npm install
  -> npm run build

Docker
  -> build backend image
  -> build frontend image
```

La CI también se ejecuta para PR y pushes en `feature/*`. Cuando los tres checks pasan, un PR dirigido a `feature/*` se integra automáticamente. Al cerrar un PR ya integrado en una rama `feature/*`, se crea un PR hacia `develop`; al integrarlo en `develop`, se crea otro hacia `main`.

Los PR de promoción se configuran con auto-merge. El de `develop` a `main` solo se integrará cuando GitHub confirme los checks requeridos y la aprobación humana configurada en las reglas de ramas.

### Configuración requerida en GitHub

1. En **Settings → General → Pull Requests**, habilita **Allow auto-merge**.
2. Crea un fine-grained personal access token limitado a este repositorio, con permisos **Contents: Read and write** y **Pull requests: Read and write**.
3. Guarda el token en **Settings → Secrets and variables → Actions** con el nombre `PR_AUTOMATION_TOKEN`. El workflow lo usa para crear PR y habilitar merges automáticos; no lo escribas en el código.
4. Protege `develop` exigiendo PR y los checks `backend`, `frontend` y `docker`.
5. Protege `main` exigiendo PR, al menos una aprobación y los checks `backend`, `frontend` y `docker`. Bloquea los pushes directos a `main`.

No exijas una aprobación en `develop` si quieres que esa promoción se integre automáticamente cuando pasen los checks.

Para una cohorte real recomiendo que después de ejecutar una vez `npm install` se confirme el `package-lock.json` al repositorio y se cambien los comandos a `npm ci` para builds completamente reproducibles.

---

# 17. Despliegue gratuito recomendado

## Arquitectura cloud académica

```text
                         USER
                           |
                           v
                    +-------------+
                    |   Vercel    |
                    |   Angular   |
                    +------+------+ 
                           |
                         HTTPS
                           |
                           v
                    +-------------+
                    |   Render    |
                    | Spring Boot |
                    +------+------+ 
                           |
          +----------------+------------------+----------------+
          |                |                  |                |
          v                v                  v                v
        Neon            Upstash           CloudAMQP         Gemini
      PostgreSQL         Redis             RabbitMQ           API
```

Esta topología prioriza costo $0 para demos y formación.

En un entorno empresarial real podrían reemplazarse por RDS/Aurora, ElastiCache, Amazon MQ/MSK, Kubernetes, ECS, etc.

---

# 18. Crear PostgreSQL gratuito en Neon

1. Crea una cuenta en Neon.
2. Crea un proyecto PostgreSQL.
3. Abre `Connection Details`.
4. Copia host, database, user y password.
5. Usa una URL JDBC.

Ejemplo conceptual:

```text
jdbc:postgresql://HOST/DATABASE?sslmode=require
```

Variables para Render:

```env
DATABASE_URL=jdbc:postgresql://HOST/DATABASE?sslmode=require
DATABASE_USER=USUARIO
DATABASE_PASSWORD=PASSWORD
```

No subas estas credenciales al repositorio.

Neon mantiene un plan gratuito adecuado para aprendizaje y prototipos. Verifica límites actuales en:

https://neon.com/pricing

---

# 19. Crear Redis gratuito en Upstash

1. Ingresa a Upstash.
2. Crea una Redis Database Free.
3. Selecciona una región cercana cuando sea posible.
4. Copia la URL TLS de Redis.

Debe verse conceptualmente como:

```text
rediss://default:PASSWORD@HOST:PORT
```

En Render:

```env
REDIS_URL=rediss://...
```

No coloques la URL en Angular.

Página oficial:

https://upstash.com/pricing/redis

---

# 20. Crear RabbitMQ gratuito en CloudAMQP

1. Crea una cuenta en CloudAMQP.
2. Crea una instancia.
3. Selecciona el plan gratuito **Little Lemur** si sigue disponible.
4. Abre los detalles de la instancia.
5. Copia la AMQP URL.

Ejemplo:

```text
amqps://USER:PASSWORD@HOST/VHOST
```

En Render:

```env
RABBITMQ_URL=amqps://...
```

Página oficial:

https://www.cloudamqp.com/plans.html

La cola es durable. Si el backend gratuito se duerme, los mensajes pueden permanecer en RabbitMQ hasta que la aplicación vuelva a conectarse.

---

# 21. Gemini Developer API

1. Abre Google AI Studio.
2. Crea una API key para un proyecto de laboratorio.
3. Configúrala únicamente en Render:

```env
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-3.8-flash
```

Página de precios/límites:

https://ai.google.dev/gemini-api/docs/pricing

El modelo es parametrizable. Si Google cambia los modelos disponibles, cambia `GEMINI_MODEL` sin modificar la arquitectura.

---

# 22. Desplegar Spring Boot en Render

## Paso 1

Sube este repositorio a GitHub.

## Paso 2

En Render:

```text
New -> Web Service
```

Conecta GitHub.

## Paso 3

Selecciona el repositorio.

Configura:

```text
Root Directory: backend
Runtime: Docker
Plan: Free
```

Render encontrará:

```text
backend/Dockerfile
```

## Paso 4 — Variables

Agrega:

```env
DATABASE_URL=jdbc:postgresql://...
DATABASE_USER=...
DATABASE_PASSWORD=...
REDIS_URL=rediss://...
RABBITMQ_URL=amqps://...
JWT_SECRET=...
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-3.8-flash
CORS_ALLOWED_ORIGINS=https://TU-FRONTEND.vercel.app
```

`PORT` normalmente es proporcionado por Render y Spring lo consume mediante:

```yaml
server:
  port: ${PORT:8080}
```

## Paso 5 — Health check

Configura:

```text
/actuator/health/liveness
```

Usamos **liveness** para comprobar que el proceso Java sigue vivo sin convertir una caída temporal de Redis/RabbitMQ en un reinicio innecesario. El endpoint general `/actuator/health` sigue siendo útil para diagnosticar dependencias.

## Paso 6

Deploy.

Obtendrás algo parecido a:

```text
https://skillbridge-api.onrender.com
```

Prueba:

```text
https://skillbridge-api.onrender.com/actuator/health
```

### Importante sobre Render Free

Los servicios gratuitos pueden entrar en suspensión por inactividad y presentar cold start. Es apropiado para laboratorio, demostraciones y portafolio, no para una aplicación que requiera disponibilidad continua.

Docs:

https://render.com/docs/free

---

# 23. Desplegar Angular en Vercel

El frontend incorpora configuración runtime generada durante build.

No necesitas cambiar el código TypeScript para cada ambiente.

## Paso 1

En Vercel:

```text
Add New -> Project
```

Importa el mismo repositorio.

## Paso 2

Configura:

```text
Root Directory: frontend
```

El `vercel.json` ya especifica el build y el fallback del SPA.

## Paso 3 — Variable

Agrega:

```env
API_URL=https://TU-BACKEND.onrender.com/api
```

Durante:

```bash
npm run build
```

se ejecuta:

```text
scripts/generate-env.mjs
```

y se genera:

```javascript
window.__env = {
  API_URL: "https://TU-BACKEND.onrender.com/api"
};
```

## Paso 4

Deploy.

Obtendrás:

```text
https://skillbridge-ai.vercel.app
```

## Paso 5 — CORS

Vuelve a Render y asegúrate de que:

```env
CORS_ALLOWED_ORIGINS=https://skillbridge-ai.vercel.app
```

Si tienes preview URLs, agrega solo las que realmente necesites, separadas por coma.

Pricing:

https://vercel.com/pricing

---

# 24. ¿Y Nginx en producción si Vercel sirve Angular?

Esta es una decisión arquitectónica importante.

### Local/containerizado

```text
Nginx -> Angular + reverse proxy -> Spring
```

### Vercel gratuito

```text
Vercel Edge/CDN -> Angular
Angular -> HTTPS -> Render Spring
```

En Vercel, su infraestructura cumple el rol de servir y enrutar los archivos estáticos. Por eso el contenedor Nginx no participa en esa topología.

Sin embargo, **la imagen Docker de Angular + Nginx sigue siendo válida para cualquier plataforma que ejecute contenedores**.

Si quieres demostrar Nginx también en cloud, puedes desplegar `frontend/Dockerfile` como un segundo Web Service de Render y configurar `BACKEND_URL=https://TU-BACKEND.onrender.com`. Para una cohorte numerosa no es la opción $0 más eficiente porque consume más horas de cómputo.

---

# 25. Variables de producción

## Backend

| Variable | Propósito |
|---|---|
| `DATABASE_URL` | JDBC PostgreSQL |
| `DATABASE_USER` | usuario DB |
| `DATABASE_PASSWORD` | password DB |
| `REDIS_URL` | Redis local/cloud |
| `RABBITMQ_URL` | AMQP/AMQPS |
| `JWT_SECRET` | firma JWT |
| `JWT_EXPIRATION_MINUTES` | expiración del token |
| `GEMINI_API_KEY` | key IA |
| `GEMINI_MODEL` | modelo IA |
| `CORS_ALLOWED_ORIGINS` | orígenes frontend |
| `OFFERINGS_CACHE_TTL_MINUTES` | TTL de catálogo |
| `DB_POOL_SIZE` | Hikari pool |

## Frontend

| Variable | Propósito |
|---|---|
| `API_URL` | URL pública del backend + `/api` |

---

# 26. Seguridad

El starter implementa:

- password BCrypt;
- JWT firmado;
- API stateless;
- rutas públicas y autenticadas;
- CORS parametrizable;
- validación de requests;
- secretos por variables de entorno;
- usuario no-root en el contenedor Java;
- headers básicos en Nginx.

## Los estudiantes deben agregar

- autorización por método con `@PreAuthorize`;
- refresh token o sesiones seguras según diseño;
- política de contraseñas;
- rate limiting;
- account lock / brute-force protection;
- auditoría;
- rotación de secretos;
- manejo seguro de PII;
- autorización por recurso, no solo por rol.

---

# 27. Manejo de errores

El backend usa `ProblemDetail` de Spring, basado en RFC 7807/9457-style problem responses.

Ejemplo conceptual:

```json
{
  "type": "about:blank",
  "title": "Business rule violation",
  "status": 422,
  "detail": "La reserva debe programarse en una fecha futura"
}
```

No deben responder simplemente:

```json
{"error":"algo salió mal"}
```

para todos los casos.

---

# 28. Migraciones

No uses en producción:

```yaml
hibernate:
  ddl-auto: create
```

Este proyecto usa:

```yaml
hibernate:
  ddl-auto: validate
```

más Flyway:

```text
backend/src/main/resources/db/migration/
```

Primera migración:

```text
V1__init.sql
```

Los cambios siguientes deberían ser:

```text
V2__add_provider_profile.sql
V3__add_availability.sql
V4__add_notifications.sql
```

No edites una migración que ya fue ejecutada en ambientes compartidos. Crea una nueva.

---

# 29. Git Flow sugerido para las células

```text
main
  |
  +--- develop
          |
          +--- feature/auth
          +--- feature/catalog
          +--- feature/bookings
          +--- feature/redis-cache
          +--- feature/rabbit-events
          +--- feature/ai
```

Flujo:

```text
feature/*
   |
Pull Request
   |
CI
   |
Code Review
   |
develop
   |
release
   |
main
```

No permitir push directo a `main` durante el ejercicio.

---

# 30. Definition of Done sugerida

Una historia está terminada cuando:

- cumple criterios funcionales;
- respeta dependencias de arquitectura;
- tiene validaciones;
- maneja errores;
- incluye pruebas relevantes;
- pasa CI;
- no contiene secretos;
- Swagger/OpenAPI refleja el contrato;
- tiene logs útiles sin datos sensibles;
- se puede ejecutar desde Docker;
- está revisada por otro integrante de la célula.

---

# 31. Roadmap de evolución profesional

## Nivel 1 — Starter actual

```text
Angular
Spring Boot
JWT
PostgreSQL
Redis
RabbitMQ
Gemini
Docker
Nginx
CI
Metrics
```

## Nivel 2

Agregar:

- CRUD de proveedores.
- disponibilidad real;
- estados de reserva;
- roles;
- invalidación de cache;
- Testcontainers;
- DLQ inspection/recovery;
- notificaciones persistentes.

## Nivel 3

Agregar:

- Transactional Outbox;
- idempotency keys;
- optimistic locking;
- retry con backoff;
- circuit breaker con Resilience4j;
- tracing distribuido/OpenTelemetry;
- structured logging;
- SonarCloud;
- OWASP dependency scanning.

## Nivel 4

Separar un bounded context:

```text
Modular Monolith
      |
      +--> Notification Service
      |
      +--> Recommendation Service
```

No conviertas todo a microservicios por moda. Divide únicamente cuando exista una razón de dominio, despliegue, escala o autonomía.

## Nivel 5

```text
Container Registry
        |
        v
Kubernetes
        |
        v
Helm
        |
        +--> ConfigMap
        +--> Secret
        +--> Deployment
        +--> Service
        +--> Ingress
        +--> HPA
```

---

# 32. Retos arquitectónicos para evaluación

Las células deben poder responder:

1. ¿Por qué Redis es un adaptador y no parte del dominio?
2. ¿Qué pasa si Redis está caído?
3. ¿Qué pasa si PostgreSQL guarda la reserva pero RabbitMQ falla?
4. ¿Qué problema resuelve Outbox?
5. ¿Por qué una DLQ es necesaria?
6. ¿Cómo se evita procesar dos veces el mismo evento?
7. ¿Por qué la API key de IA no puede estar en Angular?
8. ¿Qué diferencia existe entre Dockerfile y Compose?
9. ¿Por qué Nginx no necesita conocer PostgreSQL?
10. ¿Qué dependencias podría reemplazar sin modificar el dominio?
11. ¿Cuál información puede cachearse y cuál no?
12. ¿Cómo invalidarían el catálogo cuando un proveedor modifica un servicio?
13. ¿Cómo medirían que Redis realmente mejoró la latencia?
14. ¿Cuándo separarían el consumer en otro servicio?
15. ¿Qué implica que Render Free pueda dormirse?

---

# 33. Comandos útiles

Levantar:

```bash
docker compose up --build -d
```

Estado:

```bash
docker compose ps
```

Logs backend:

```bash
docker compose logs -f backend
```

Logs RabbitMQ:

```bash
docker compose logs -f rabbitmq
```

Entrar a PostgreSQL:

```bash
docker compose exec postgres psql -U skillbridge -d skillbridge
```

Entrar a Redis:

```bash
docker compose exec redis redis-cli
```

Recrear únicamente backend:

```bash
docker compose up -d --build backend
```

Detener:

```bash
docker compose down
```

Eliminar también datos locales:

```bash
docker compose down -v
```

Observabilidad:

```bash
docker compose --profile observability up -d
```

---

# 34. Troubleshooting

## `port is already allocated`

Busca el proceso que usa el puerto o modifica el puerto del host en `docker-compose.yml`.

Ejemplo:

```yaml
ports:
  - "8089:80"
```

## Backend no conecta a PostgreSQL

Dentro de Docker **no uses**:

```text
localhost:5432
```

El hostname es el nombre del servicio:

```text
postgres:5432
```

## Backend no conecta a Redis

Docker:

```text
redis://redis:6379
```

Cloud normalmente:

```text
rediss://...
```

## RabbitMQ no conecta

Local:

```text
amqp://guest:guest@rabbitmq:5672
```

Cloud:

```text
amqps://...
```

## IA responde que no existe API key

Configura:

```env
GEMINI_API_KEY=...
```

y reconstruye/reinicia backend.

## Angular funciona pero API da CORS

En backend revisa:

```env
CORS_ALLOWED_ORIGINS=https://tu-frontend.vercel.app
```

No uses `*` con credenciales como solución permanente.

## Render tarda al abrir

En plan gratuito puede existir cold start después de inactividad.

---

# 35. Qué NO hacer

No subir:

```text
.env
passwords
tokens
API keys
connection strings privadas
```

No poner en Angular:

```text
JWT_SECRET
DATABASE_PASSWORD
REDIS_URL
RABBITMQ_URL
GEMINI_API_KEY
```

En un producto real, restringe `/actuator/prometheus` a una red privada o autenticación; en este starter se deja accesible para que Prometheus local pueda hacer scraping.

No usar Docker Compose como excusa para meter todas las credenciales directamente en YAML.

No crear microservicios sin una razón.

No usar Redis como base de datos principal para información transaccional de este caso de uso.

No llamar a Gemini directamente desde Angular con una key privada.

---

# 36. Servicios gratuitos: referencia al 29-09-2026

Este proyecto fue preparado pensando en opciones disponibles para formación en septiembre de 2026:

- Render dispone de Web Services gratuitos para pruebas/hobby, con suspensión por inactividad y límites mensuales.
- Vercel mantiene plan Hobby gratuito para proyectos personales.
- Neon mantiene Postgres Free.
- Upstash ofrece Redis Free.
- CloudAMQP ofrece un plan RabbitMQ gratuito de desarrollo.
- Gemini Developer API dispone de nivel gratuito para modelos elegibles.

Verifica siempre condiciones y cuotas actuales antes de iniciar una nueva cohorte:

- https://render.com/docs/free
- https://vercel.com/pricing
- https://neon.com/pricing
- https://upstash.com/pricing/redis
- https://www.cloudamqp.com/plans.html
- https://ai.google.dev/gemini-api/docs/pricing

---

# 37. Archivos complementarios

Lee también:

```text
docs/ARCHITECTURE.md
docs/BACKLOG.md
docs/DEPLOYMENT-CHECKLIST.md
docs/INSTRUCTOR-GUIDE.md
```

`BACKLOG.md` está pensado para trabajar el proyecto por sprints/células.

---

# 38. Objetivo final para las células

El proyecto debe llegar a un estado donde un evaluador pueda:

```bash
git clone <repo>
cd <repo>
cp .env.example .env
docker compose up --build
```

y tener un entorno funcional sin instalar manualmente PostgreSQL, Redis o RabbitMQ.

Además debe existir:

- URL pública del frontend;
- URL pública del backend;
- base de datos cloud;
- Redis cloud;
- RabbitMQ cloud;
- integración de IA;
- CI verde;
- README actualizado;
- diagrama de arquitectura;
- evidencia de pruebas;
- explicación de decisiones técnicas.

La evaluación no debería limitarse a "funciona". La célula debe poder **explicar por qué está diseñada de esa manera, qué trade-offs tomó y cómo evolucionaría la solución para producción real**.

---

## Licencia de uso educativo

Este starter puede ser adaptado libremente como material de formación. Las credenciales de proveedores externos y los límites de sus planes son responsabilidad de cada equipo.
# SkillBridge-AI-JAVA-Angular
# SkillBridge-AI-JAVA-Angular
