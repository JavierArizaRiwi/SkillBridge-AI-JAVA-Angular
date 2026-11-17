# SkillBridge AI

Plataforma de intermediación de talento y reservas de mentoría técnica que integra asistencia conversacional multimodal basada en Inteligencia Artificial, arquitectura desacoplada por eventos y un microservicio independiente de auditoría.

---

## 1. Problema que resuelve y funcionalidades verificadas

SkillBridge AI resuelve la gestión integral de mentorías técnicas garantizando consistencia transaccional, trazabilidad de eventos y asistencia impulsada por IA para el descubrimiento de servicios.

### Funcionalidades principales verificadas:
- **Catálogo público optimizado**: Consulta de ofertas activas con estrategia *Cache-Aside* respaldada en Redis (`TTL 10 min`).
- **Gestión de reservas idempotente**: Creación transaccional (`POST /api/bookings`) con cabecera obligatoria `Idempotency-Key` y restricciones de unicidad.
- **Cancelación con política de 24 horas**: Cancelación (`PATCH /api/bookings/{id}/cancel`) sujeta a 24 horas de antelación y registro histórico de estado.
- **Recomendaciones de IA multimodales**: Asistente contextualizado con el catálogo mediante Google Gemini (`gemini-3.8-flash`) u OpenAI, transcripción de voz (Groq Whisper / Gemini), análisis visual de imágenes y síntesis de voz (ElevenLabs).
- **Procesamiento de pagos**: Autorización vía Stripe (`PaymentIntent` y webhooks criptográficos) o adaptador simulado para pruebas locales.
- **Auditoría inmutable distribuida**: Ingestión asíncrona de eventos de dominio a través de Apache Kafka y almacenamiento en MongoDB.

---

## 2. Arquitectura del sistema y responsabilidades

El sistema implementa un paradigma híbrido compuesto por tres subsistemas autónomos:

| Subsistema | Directorio | Tecnologías | Responsabilidad operativa |
|---|---|---|---|
| **Core API Backend** | [`backend/`](backend/) | Java 21, Spring Boot 3.5.6, Spring AI | Monolito modular con Arquitectura Hexagonal (*Ports & Adapters*). Aloja autenticación, catálogo, reservas, pagos y orquestación de IA. |
| **Client Frontend** | [`frontend/`](frontend/) | Angular 20, Signals, Tailwind CSS v4 | SPA de cliente y proveedor para navegación de catálogo, creación de reservas, pasarela de pago y chat multimodal. |
| **Audit Microservice** | [`audit-service/`](audit-service/) | Spring Boot 3.5.6, Kafka, MongoDB 7, Angular 20 | Microservicio desacoplado que consume eventos del topic `booking-events`, persiste registros en MongoDB y ofrece una consola de auditoría. |

### Reglas e invariantes arquitectónicos:
- **Aislamiento de dominio**: El núcleo de dominio (`com.riwi.skillbridge.domain`) es Java puro, libre de dependencias de Spring o persistencia.
- **Desacoplamiento de eventos**: Notificaciones operativas vía RabbitMQ (exchange con reintentos exponenciales y DLQ); auditoría persistente vía Apache Kafka.
- **Trazabilidad transversal**: Propagación obligatoria de `X-Correlation-Id` en peticiones HTTP y envoltorios `BusinessEvent<T>`.

---

## 3. Tecnologías principales

- **Lenguajes y Runtimes**: Java 21 (Eclipse Temurin), TypeScript 5.9, Node.js 22 LTS.
- **Frameworks**: Spring Boot 3.5.6 (Spring Security 6, Spring Data JPA, Spring AI 1.1.8), Angular 20.
- **Bases de datos**: PostgreSQL 17 (Sistema de registro con Flyway `V1`–`V7`), MongoDB 7 (Eventos de auditoría).
- **Caché y Mensajería**: Redis 8 (Caché de catálogo), RabbitMQ 4 (Colas y DLQ), Apache Kafka 7.8 KRaft (Streaming).
- **Observabilidad**: Prometheus 3.5.0, Grafana 12.1.1, Mailpit (Mock SMTP).

---

## 4. Prerrequisitos de desarrollo local

- **Docker y Docker Compose**: Motor Docker v24+ y plugin Compose v2+.
- **Java Development Kit (JDK)**: Java 21 LTS (recomendado Eclipse Temurin 21).
- **Maven**: Maven 3.9+ instalado en el sistema (*el repositorio no dispone de wrapper `mvnw`*).
- **Node.js**: Versión 22 LTS (o 20+) con gestor de paquetes `npm`.
- **Cliente cURL / HTTP**: Para validaciones de API y ejecución de pruebas manuales.

---

## 5. Instalación y configuración inicial

### 1. Clonar el repositorio
```bash
git clone <URL-DEL-REPOSITORIO>
cd SkillBridge-AI-JAVA-Angular
```

### 2. Configurar variables de entorno
Copia la plantilla base antes de levantar los contenedores:
```bash
cp .env.example .env
```
Configura en `.env` las credenciales requeridas para servicios externos (como `GEMINI_API_KEY` o `STRIPE_SECRET_KEY`). En modo local, los valores predeterminados permiten iniciar la infraestructura sin conexión a servicios de pago reales.

> [!WARNING]
> Nunca confirmes archivos `.env` ni credenciales reales en el repositorio.

---

## 6. Comandos verificados de ejecución

### Despliegue con Docker Compose
El entorno completo (12 servicios) se levanta con el comando oficial verificado:
```bash
docker compose up --build -d
```

> [!TIP]
> **Atajos con `Makefile`**: El `Makefile` incluye atajos operativos: `make up` (iniciar todo), `make down` (detener), `make logs` (ver trazas), `make ps` (estado de contenedores) y `make build` (recompilar).

### Puntos de acceso y URLs locales verificadas

| Servicio | URL / Puerto | Credenciales / Notas |
|---|---|---|
| **SkillBridge Web (Frontend)** | `http://localhost:8088` | Portal principal de usuario |
| **SkillBridge API (Backend)** | `http://localhost:8080` | Swagger UI: `http://localhost:8080/swagger-ui.html` |
| **Audit Admin (Frontend)** | `http://localhost:8090` | Consola de auditoría administrativa |
| **Audit API (Backend)** | `http://localhost:8081` | Endpoints REST: `/api/audit/events`, `/stats` |
| **RabbitMQ Management** | `http://localhost:15672` | Usuario: `guest` / Clave: `guest` |
| **Mailpit (Web SMTP UI)** | `http://localhost:8025` | Inspección de correos transaccionales |
| **Prometheus** | `http://localhost:9090` | Métricas de Actuator y JVM |
| **Grafana** | `http://localhost:3000` | Usuario: `admin` / Clave: `admin` |

### Ejecución individual fuera de Docker (opcional)
- **Backend**: `cd backend && mvn spring-boot:run`
- **Frontend**: `cd frontend && npm ci && npm start`

---

## 7. Pruebas y validación del sistema

### Backend Principal (`backend/`)
```bash
cd backend
mvn test     # Pruebas unitarias y de capa (requiere Docker daemon por BookingPersistenceAdapterTest)
mvn verify   # Pruebas de integración con Testcontainers
```
> [!NOTE]
> `BookingPersistenceAdapterTest` requiere el demonio Docker activo durante `mvn test`. Las clases `NotificationFlowIT` y `JpaOfferingRepositoryTest` están temporalmente marcadas con `@Disabled` debido a incompatibilidades de entorno.

### Frontend Cliente (`frontend/`)
```bash
cd frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

### Microservicio de Auditoría (`audit-service/`)
```bash
cd audit-service/backend && mvn test
cd ../frontend && npm ci && npm test -- --watch=false --browsers=ChromeHeadless
```

---

## 8. Navegación documental del repositorio

La suite técnica completa se encuentra en [`docs/`](docs/):

- **Contexto general y alcance**: [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md)
- **Arquitectura y flujo de eventos**: [`docs/architecture/overview.md`](docs/architecture/overview.md)
- **Módulo Backend (API)**: [`docs/MODULES/backend.md`](docs/MODULES/backend.md)
- **Módulo Frontend (Web)**: [`docs/MODULES/frontend.md`](docs/MODULES/frontend.md)
- **Módulo Microservicio de Auditoría**: [`docs/MODULES/audit-service.md`](docs/MODULES/audit-service.md)
- **Integraciones y brokers (Postgres, Redis, RabbitMQ, Kafka, IA)**: [`docs/INTEGRATIONS.md`](docs/INTEGRATIONS.md)
- **Modelo de seguridad y control de acceso (RBAC, CSRF, JWT)**: [`docs/SECURITY-MODEL.md`](docs/SECURITY-MODEL.md)
- **Estrategia y suites de testing**: [`docs/TESTING.md`](docs/TESTING.md)
- **Guía de contribución y flujo de trabajo**: [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md)
- **Lista de verificación de despliegue**: [`docs/DEPLOYMENT-CHECKLIST.md`](docs/DEPLOYMENT-CHECKLIST.md)
- **Guía de despliegue cloud (laboratorio gratuito)**: [`docs/guides/free-tier-deployment.md`](docs/guides/free-tier-deployment.md)
- **Registro canónico de riesgos y deuda técnica**: [`docs/RISK-REGISTER.md`](docs/RISK-REGISTER.md)
- **Instrucciones para agentes de IA**: [`AGENTS.md`](AGENTS.md) | [`CLAUDE.md`](CLAUDE.md)

---

## 9. Contribución y reporte de problemas

Las contribuciones se rigen por la [Guía de Contribución](docs/CONTRIBUTING.md):
1. **Flujo de ramas**: Basar el trabajo en ramas `feature/<nombre>` creadas a partir de `develop`.
2. **Definition of Done**: Validar límites arquitectónicos, pruebas automatizadas (`mvn test`, `npm test`) y ausencia de secretos antes de abrir Pull Requests.
3. **Decisiones arquitectónicas**: Registrar cambios en dependencias o puertos mediante un ADR en [`docs/adr/`](docs/adr/).
4. **Reporte de incidencias y riesgos**: Consultar y registrar vulnerabilidades o deuda técnica en el [Registro de Riesgos](docs/RISK-REGISTER.md) y en [`docs/audit/findings.md`](docs/audit/findings.md).
