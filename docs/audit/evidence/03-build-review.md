# Evidencia de Auditoría: Calidad de Build, Pruebas y DevOps

## 1. Metadatos de la Auditoría

- **Fecha UTC de ejecución:** `2026-10-21T19:04:34Z`
- **Rama git activa:** `develop`
- **Commit HEAD auditado:** `cdd009aa8c9ba351fa8bb50bb1ad0a381e259793`
- **Mensaje del commit:** `Merge pull request #73 from Danilo-Doria/Bugfix/BUG-04-Categoria`
- **Estado del árbol de trabajo (`git status --short`):**
  ```text
  AM docs/audit/findings.md
  A  scripts/repo_audit.py
  ?? docs/audit/evidence/
  ?? docs/audit/generated/
  ```
- **Archivo generado por esta auditoría:** `docs/audit/evidence/03-build-review.md`

---

## 2. Localización e Inventario de Archivos Reales de Build

La estructura del repositorio corresponde a un monorepositorio con múltiples subsistemas desacoplados (backend principal, frontend principal, y el servicio independiente `audit-service` con su propio backend y frontend). No se asumen ubicaciones predeterminadas; se verificaron las rutas reales en el sistema de archivos:

| Componente / Subsistema | Archivo real localizado | Propósito y tecnología |
|---|---|---|
| **Raíz (Orquestación local)** | [Makefile](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/Makefile) | Definición de comandos de orquestación (`up`, `down`, `build`, `test-*`). |
| **Raíz (Contenedores)** | [docker-compose.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docker-compose.yml) | Composición de servicios: PostgreSQL, Redis, RabbitMQ, Kafka, MongoDB, backend, frontend, audit y observabilidad. |
| **Raíz (Control de versiones)** | [.gitignore](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.gitignore) | Patrones de exclusión de artefactos de compilación e IDE. |
| **Raíz (Configuración IDE/Calidad)** | [qodana.yaml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/qodana.yaml) | Configuración de análisis estático JetBrains Qodana. |
| **CI / CD (GitHub Actions)** | [.github/workflows/ci.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/workflows/ci.yml) | Pipeline principal de integración continua (backend, frontend, docker build). |
| **CI / CD (Calidad de código)** | [.github/workflows/qodana_code_quality.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/workflows/qodana_code_quality.yml) | Workflow de análisis estático automatizado con Qodana Cloud. |
| **Maven (Configuración de red CI)** | [.github/.maven/settings.xml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/.maven/settings.xml) | Mirror explícito a Maven Central para builds en GitHub Actions. |
| **Backend Principal** | [backend/pom.xml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/pom.xml) | Descriptor Maven (Spring Boot 3.5.6, Java 21, Failsafe, JaCoCo). |
| **Backend Principal** | [backend/Dockerfile](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/Dockerfile) | Construcción multi-stage (`maven:3.9.16-eclipse-temurin-21` -> `eclipse-temurin:21-jre`). |
| **Backend Principal** | [backend/.dockerignore](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/.dockerignore) | Exclusiones para el contexto Docker de backend. |
| **Backend Principal** | [backend/test_kafka.sh](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/test_kafka.sh) | Script bash con recordatorio de ejecución de pruebas. |
| **Frontend Principal** | [frontend/package.json](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/package.json) | Manifiesto npm (Angular 20.3.0, scripts de build y test). |
| **Frontend Principal** | [frontend/package-lock.json](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/package-lock.json) | Árbol de dependencias bloqueadas (lockfile v3). |
| **Frontend Principal** | [frontend/angular.json](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/angular.json) | Configuración de targets de Angular CLI (`build`, `serve`, `test`). |
| **Frontend Principal** | [frontend/Dockerfile](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/Dockerfile) | Construcción multi-stage (`node:22-alpine` -> `nginx:stable-alpine`). |
| **Frontend Principal** | [frontend/scripts/generate-env.mjs](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/scripts/generate-env.mjs) | Script de prebuild para generación de `public/env.js`. |
| **Audit Service Backend** | [audit-service/backend/pom.xml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/pom.xml) | Descriptor Maven independiente (Spring Boot 3.5.6, MongoDB, Kafka, Testcontainers). |
| **Audit Service Backend** | [audit-service/backend/Dockerfile](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/Dockerfile) | Construcción multi-stage (`maven:3.9.6-eclipse-temurin-21-alpine` -> `eclipse-temurin:21-jre-alpine`). |
| **Audit Service Backend** | `audit-service/backend/target/` | Directorio de binarios compilados y empaquetados (`audit-service-0.0.1-SNAPSHOT.jar` de 53MB) **rastreados en git**. |
| **Audit Service Frontend** | [audit-service/frontend/package.json](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/frontend/package.json) | Manifiesto npm (Angular 20.3.0, build con `@angular/build:application`). |
| **Audit Service Frontend** | [audit-service/frontend/package-lock.json](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/frontend/package-lock.json) | Lockfile v3 independiente. |
| **Audit Service Frontend** | [audit-service/frontend/angular.json](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/frontend/angular.json) | Configuración Angular CLI (`@angular/build:application` y `@angular/build:karma`). |
| **Audit Service Frontend** | [audit-service/frontend/Dockerfile](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/frontend/Dockerfile) | Construcción multi-stage (`node:22-alpine` -> `nginx:alpine`). |
| **Scripts de Verificación y E2E** | [scripts/smoke-test.sh](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/scripts/smoke-test.sh) | Script shell para prueba de humo de liveness y catálogo público. |
| **Scripts de Verificación y E2E** | [scripts/e2e-offerings.ps1](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/scripts/e2e-offerings.ps1) | Suite E2E en PowerShell para flujos de autenticación y catálogo. |
| **Scripts de Verificación y E2E** | [scripts/e2e-admin-users.ps1](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/scripts/e2e-admin-users.ps1) | Suite E2E en PowerShell para flujos administrativos de usuarios. |
| **Scripts de Demostración** | [scripts/demo-commands.sh](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/scripts/demo-commands.sh) | Guía interactiva de comandos con invocaciones de prueba. |

### Hallazgos de ausencia arquitectural:
1. **Ausencia de `pom.xml` padre o agregador en raíz:** El repositorio no cuenta con un proyecto multimodular raíz. Cada módulo Java debe compilarse desde su subdirectorio (`backend/` o `audit-service/backend/`).
2. **Ausencia de Maven Wrapper (`mvnw` / `mvnw.cmd`):** No existe Maven Wrapper en ninguna ruta del repositorio, a pesar de que scripts y documentación señalan su uso (`./mvnw clean verify`).
3. **Ausencia de `package.json` en raíz:** No existe orquestación unificada de Node/npm para workspaces o monorepos (Lerna, Nx, npm workspaces o Turborepo).

---

## 3. Requisitos de Entorno y Comandos de Ejecución Reproducibles

### 3.1. Versiones Requeridas vs. Detectadas

| Herramienta | Versión Declarada en Configuración | Evidencia en Archivo Real | Versión Detectada Localmente | Observación |
|---|---|---|---|---|
| **Java** | 21 | [backend/pom.xml#L19](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/pom.xml#L19), [audit-service/backend/pom.xml#L19](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/pom.xml#L19), [.github/workflows/ci.yml#L23](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/workflows/ci.yml#L23) | `OpenJDK 21.0.12.1` | Cumple el requisito. Compatible con Temurin 21. |
| **Node.js** | 22 | [.github/workflows/ci.yml#L37](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/workflows/ci.yml#L37), [frontend/Dockerfile#L1](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/Dockerfile#L1), [audit-service/frontend/Dockerfile#L1](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/frontend/Dockerfile#L1) | `v24.22.0` | CI fija Node 22 (`setup-node@v4`); el entorno local ejecuta Node 24. |
| **npm** | >= 10 (lockfile v3) | [frontend/package-lock.json#L3](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/package-lock.json#L3) | `11.19.1` | Soporta lockfileVersion 3. |
| **Maven** | 3.9.x (Dockerfiles) / Sistema | [backend/Dockerfile#L1](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/Dockerfile#L1), [audit-service/backend/Dockerfile#L1](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/Dockerfile#L1) | `Apache Maven 3.8.7` | Al no existir `mvnw`, el build local depende del Maven del sistema. |
| **Docker** | Compatible Compose v2 | [docker-compose.yml#L1](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docker-compose.yml#L1) | `Docker 29.5.3`, `Compose v2.39.2` | Cumple. Requerido para Testcontainers y Docker Compose. |
| **Chrome / Chromium** | Headless Chrome para Karma | [.github/workflows/ci.yml#L43](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/workflows/ci.yml#L43) | `/usr/bin/google-chrome` | Instalado en el sistema anfitrión. |

---

### 3.2. Comandos de Build y Ejecución Paso a Paso

#### A. Backend Principal (`backend/`)

- **Validación sintáctica del descriptor POM (sin descargas ni red):**
  ```bash
  cd backend && mvn -o validate
  ```
- **Compilación de código productivo:**
  ```bash
  cd backend && mvn clean compile
  ```
- **Pruebas unitarias puras (sin dependencia de Docker/Testcontainers):**
  > [!NOTE]
  > La clase `BookingPersistenceAdapterTest` se nombra con sufijo `*Test` pero instancia un contenedor PostgreSQL vía Testcontainers. Para ejecutar pruebas puramente unitarias sin levantar Docker:
  ```bash
  cd backend && mvn test -Dtest="!BookingPersistenceAdapterTest"
  ```
- **Pruebas unitarias completas (requiere Docker daemon para Testcontainers):**
  ```bash
  cd backend && mvn test
  ```
- **Pruebas de integración (`*IT.java` vía `maven-failsafe-plugin`):**
  > [!NOTE]
  > Requiere Docker activo para levantar contenedores PostgreSQL 17 y RabbitMQ 4.
  ```bash
  cd backend && mvn failsafe:integration-test failsafe:verify
  ```
- **Verificación completa y empaquetado (idéntico al pipeline de CI):**
  ```bash
  cd backend && mvn -s ../.github/.maven/settings.xml -B clean verify
  ```
- **Empaquetado omitiendo pruebas:**
  ```bash
  cd backend && mvn clean package -DskipTests
  ```

#### B. Frontend Principal (`frontend/`)

- **Instalación determinista de dependencias:**
  ```bash
  cd frontend && npm ci
  ```
- **Generación de configuración de entorno (prebuild manual):**
  ```bash
  cd frontend && node scripts/generate-env.mjs
  ```
- **Ejecución de pruebas unitarias headless (idéntico a CI):**
  ```bash
  cd frontend && npm run test -- --watch=false --browsers=ChromeHeadless
  ```
- **Compilación de producción:**
  ```bash
  cd frontend && npm run build
  ```
- **Servidor de desarrollo local:**
  ```bash
  cd frontend && npm run start
  ```

#### C. Servicio de Auditoría (`audit-service/`)

- **Backend: Validación y empaquetado:**
  ```bash
  cd audit-service/backend && mvn clean package -DskipTests
  ```
- **Backend: Pruebas unitarias (requiere Docker para MongoDB Testcontainer):**
  ```bash
  cd audit-service/backend && mvn test
  ```
- **Frontend: Instalación y construcción de producción:**
  ```bash
  cd audit-service/frontend && npm ci && npm run build --configuration=production
  ```

#### D. Despliegue Local Multicontenedor (Docker Compose)

- **Levantar infraestructura y aplicaciones completas:**
  ```bash
  docker compose up --build -d
  ```
- **Levantar con perfil de observabilidad (Prometheus + Grafana):**
  ```bash
  docker compose --profile observability up --build -d
  ```
- **Comprobación de salud rápida (Smoke test):**
  ```bash
  sh scripts/smoke-test.sh http://localhost:8080
  ```
- **Apagado y limpieza de volúmenes:**
  ```bash
  docker compose down -v --remove-orphans
  ```

---

## 4. Mapa y Matriz de Pruebas

El repositorio contiene suites de pruebas distribuidas en el backend principal, frontend principal, y el servicio de auditoría:

```mermaid
flowchart TD
    subgraph Repo["SkillBridge AI - Mapa de Pruebas"]
        subgraph Backend["backend/src/test (37 clases, 188 métodos)"]
            B_POJO["Unitarias Puras / Dominio (47 métodos)<br/>11 clases POJO"]
            B_MOCK["Unitarias / Servicios con Mocks (87 métodos)<br/>18 clases Mockito"]
            B_MVC["Slices Web / Seguridad (39 métodos)<br/>3 clases @WebMvcTest"]
            B_PERS["Persistencia Testcontainers (1 método activo, 1 disabled)<br/>BookingPersistenceAdapterTest, JpaOfferingRepositoryTest"]
            B_INT["Integración Completa (8 métodos activos, 4 disabled)<br/>BookingCancellationIT, NotificationFlowIT"]
        end
        subgraph Frontend["frontend/src/app (3 specs, 10 it)"]
            F_UNIT["Unitarias Jasmine / Angular TestBed<br/>stripe.service (4), payment-modal (4), auth-guard (2)"]
        end
        subgraph AuditBackend["audit-service/backend/src/test (1 clase, 2 métodos)"]
            A_PERS["Persistencia Testcontainers Mongo<br/>AuditEventRepositoryTest (2)"]
        end
        subgraph AuditFrontend["audit-service/frontend/src/app (1 spec, 2 it)"]
            AF_UNIT["Prueba básica componente<br/>app.spec.ts (2)"]
        end
        subgraph E2E["Scripts E2E / Smoke (Raíz)"]
            E_OFF["scripts/e2e-offerings.ps1 (HTTP contra servidor activo)"]
            E_ADM["scripts/e2e-admin-users.ps1 (HTTP contra servidor activo)"]
            E_SMK["scripts/smoke-test.sh (curl liveness + catalog)"]
        end
    end
```

### 4.1. Inventario Detallado de Clases de Prueba

#### A. Backend Principal (`backend/src/test/java`)

| Categoría Arquitectural | Archivo de Prueba | Métodos `@Test` | Dependencias / Mecanismo | Estado de Ejecución |
|---|---|---|---|---|
| **Port / In** | [CancelBookingCommandTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/port/in/CancelBookingCommandTest.java) | 4 | POJO Unit (Java puro, JUnit 5) | ACTIVO |
| **Port / Out** | [NotificationMessageTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/port/out/NotificationMessageTest.java) | 4 | POJO Unit (Java puro, JUnit 5) | ACTIVO |
| **Contrato Dominio** | [RecommendationContractTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/recommendation/RecommendationContractTest.java) | 2 | Validación de invariantes de records | ACTIVO |
| **Domain Model** | [BookingTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/domain/model/BookingTest.java) | 4 | POJO Unit | ACTIVO |
| **Domain Policy** | [OfferingAccessPolicyTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/domain/policy/OfferingAccessPolicyTest.java) | 4 | POJO Unit | ACTIVO |
| **Domain Policy** | [UserManagementPolicyTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/domain/policy/UserManagementPolicyTest.java) | 10 | POJO Unit | ACTIVO |
| **Domain Policy** | [BookingCancellationPolicyTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/domain/service/BookingCancellationPolicyTest.java) | 5 | POJO Unit | ACTIVO |
| **Application Service** | [OfferingCacheConsistencyTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/OfferingCacheConsistencyTest.java) | 3 | Stubs en memoria (`InMemoryCache`) | ACTIVO |
| **Application Service** | [AiRecommendationServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/AiRecommendationServiceTest.java) | 1 | Mockito mocks | ACTIVO |
| **Application Service** | [AuthServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/AuthServiceTest.java) | 5 | Mockito mocks | ACTIVO |
| **Application Service** | [BookingServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/BookingServiceTest.java) | 11 | Mockito mocks | ACTIVO |
| **Application Service** | [NotificationServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/NotificationServiceTest.java) | 2 | Mockito mocks | ACTIVO |
| **Application Service** | [OfferingCommandServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/OfferingCommandServiceTest.java) | 5 | Mockito mocks | ACTIVO |
| **Application Service** | [OfferingQueryServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/OfferingQueryServiceTest.java) | 4 | Mockito mocks | ACTIVO |
| **Application Service** | [OfferingServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/OfferingServiceTest.java) | 1 | Mockito mocks | ACTIVO |
| **Application Service** | [PaymentAuthorizationServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/PaymentAuthorizationServiceTest.java) | 3 | `@ExtendWith(MockitoExtension.class)` | ACTIVO |
| **Application Service** | [UserAdminServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/application/service/UserAdminServiceTest.java) | 17 | Mockito mocks | ACTIVO |
| **Adapter In (Messaging)** | [NotificationConsumerTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationConsumerTest.java) | 2 | Mockito mocks | ACTIVO |
| **Adapter In (Messaging IT)** | [NotificationFlowIT.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java) | 4 | `@SpringBootTest`, `@Testcontainers` (RabbitMQ) | **DESHABILITADO** (`Docker Bug Testcontainers`) |
| **Adapter In (Web Slice)** | [AdminUserControllerTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserControllerTest.java) | 16 | `@WebMvcTest`, MockMvc, Security mocks | ACTIVO |
| **Adapter In (Integration IT)** | [BookingCancellationIT.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java) | 8 | `@SpringBootTest`, `@Testcontainers` (Postgres 17, RabbitMQ 4) | ACTIVO |
| **Adapter In (Web Slice)** | [BookingControllerSecurityTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingControllerSecurityTest.java) | 7 | `@WebMvcTest`, MockMvc | ACTIVO |
| **Adapter In (Web Slice)** | [OfferingSecurityTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingSecurityTest.java) | 16 | `@WebMvcTest`, MockMvc | ACTIVO |
| **Adapter In (Filter)** | [CorrelationIdFilterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/filter/CorrelationIdFilterTest.java) | 2 | MockHttpServletRequest/Response | ACTIVO |
| **Adapter In (Validator)** | [MediaFileValidatorTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/validation/MediaFileValidatorTest.java) | 5 | POJO Unit | ACTIVO |
| **Adapter In (Webhook)** | [StripeWebhookControllerTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookControllerTest.java) | 5 | `@ExtendWith(MockitoExtension.class)` | ACTIVO |
| **Adapter Out (AI)** | [GeminiAiAdapterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapterTest.java) | 8 | Mockito mock (`ChatClient`) | ACTIVO |
| **Adapter Out (AI)** | [GroqSpeechToTextAdapterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapterTest.java) | 2 | Mockito mock (`RestTemplate`) | ACTIVO |
| **Adapter Out (AI)** | [OpenAiAiAdapterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapterTest.java) | 8 | Mockito mock (`ChatClient`) | ACTIVO |
| **Adapter Out (Messaging)** | [RabbitNotificationPublisherTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java) | 3 | Mockito mock (`RabbitTemplate`) | ACTIVO |
| **Adapter Out (Notification)** | [EmailNotificationSenderTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSenderTest.java) | 4 | `@ExtendWith(MockitoExtension.class)`, Mail mock | ACTIVO |
| **Adapter Out (Payment)** | [SimulatedPaymentAdapterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/SimulatedPaymentAdapterTest.java) | 4 | POJO Unit | ACTIVO |
| **Adapter Out (Payment)** | [StripePaymentAdapterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapterTest.java) | 3 | `@ExtendWith(MockitoExtension.class)`, Feign client mock | ACTIVO |
| **Adapter Out (Persistence)** | [BookingPersistenceAdapterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapterTest.java) | 1 | `@DataJpaTest`, `@Testcontainers` (Postgres 17) | **ACTIVO (Requiere Docker)** |
| **Adapter Out (Persistence)** | [JpaOfferingRepositoryTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java) | 1 | `@DataJpaTest`, `@Testcontainers` (Postgres 17) | **DESHABILITADO** (`Docker Bug Testcontainers`) |
| **Security** | [DatabaseUserDetailsServiceTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/security/DatabaseUserDetailsServiceTest.java) | 2 | Mockito mocks | ACTIVO |
| **Security** | [JwtAuthenticationFilterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilterTest.java) | 2 | Mockito mocks | ACTIVO |

**Totales Backend:** 37 clases de prueba, 188 métodos `@Test` totales (183 activos, 5 deshabilitados). Además existe una clase de utilidad de prueba: [MockAiAdapter.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/MockAiAdapter.java) (0 tests).

#### B. Frontend Principal (`frontend/src/app`)

| Archivo de Prueba | Métodos `it(...)` | Framework / Runner | Estado |
|---|---|---|---|
| [stripe.service.spec.ts](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/src/app/core/stripe.service.spec.ts) | 4 | Jasmine / TestBed / HttpClientTestingModule | ACTIVO |
| [payment-modal.spec.ts](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/src/app/features/payment-modal/payment-modal.spec.ts) | 4 | Jasmine / TestBed / ComponentFixture | ACTIVO |
| [auth-guard.spec.ts](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/src/app/guards/auth-guard.spec.ts) | 2 | Jasmine / TestBed / RouterTestingModule | ACTIVO |

**Totales Frontend:** 3 archivos spec, 10 pruebas unitarias `it()`.

#### C. Servicio de Auditoría (`audit-service`)

| Subsistema | Archivo de Prueba | Métodos | Dependencias / Anotaciones | Estado |
|---|---|---|---|---|
| Backend | [AuditEventRepositoryTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/test/java/com/riwi/audit/repository/AuditEventRepositoryTest.java) | 2 | `@DataMongoTest`, `@Testcontainers` (`mongo:7`) | ACTIVO (Requiere Docker) |
| Frontend | [app.spec.ts](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/frontend/src/app/app.spec.ts) | 2 | Jasmine / ComponentFixture | ACTIVO |

---

### 4.2. Pruebas Deshabilitadas / Ignoradas y Condición Exacta

Se detectaron exactamente dos clases de prueba deshabilitadas en todo el repositorio mediante la anotación de JUnit 5:

1. **[NotificationFlowIT.java#L56](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java#L56):**
   ```java
   @org.junit.jupiter.api.Disabled("Docker Bug Testcontainers")
   @Testcontainers
   class NotificationFlowIT { ... }
   ```
   - **Impacto:** Deshabilita 4 pruebas de integración que validan el flujo asíncrono de notificaciones RabbitMQ (publicación, confirmación de exchange, DLQ y reintentos).
   - **Causa documentada:** `"Docker Bug Testcontainers"`.
   - **Condición técnica:** La prueba depende del arranque de `RabbitMQContainer("rabbitmq:4-management-alpine")`. Fue desactivada para evitar fallos intermitentes de inicialización de Docker / Testcontainers en entornos de desarrollo o CI.

2. **[JpaOfferingRepositoryTest.java#L16](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java#L16):**
   ```java
   @org.junit.jupiter.api.Disabled("Docker Bug Testcontainers")
   @Testcontainers
   @DataJpaTest
   @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
   class JpaOfferingRepositoryTest { ... }
   ```
   - **Impacto:** Deshabilita 1 prueba de consulta de repositorio JPA con PostgreSQL.
   - **Causa documentada:** `"Docker Bug Testcontainers"`.
   - **Condición técnica:** Requiere `PostgreSQLContainer("postgres:17-alpine")`. Irónicamente, `BookingPersistenceAdapterTest` utiliza exactamente la misma configuración de contenedor PostgreSQL y no está deshabilitada.

---

## 5. Dependencias de Infraestructura y Servicios Externos en Pruebas

### 5.1. Matriz de Dependencias Declaradas vs. Verificadas en Ejecución

| Infraestructura / Servicio | Dependencia Declarada | Verificación en Pruebas Automatizadas | Tipo de Verificación / Mock | Requiere Docker en Tests |
|---|---|---|---|---|
| **PostgreSQL 17** | `org.postgresql:postgresql`, `flyway-core` | **SÍ** | Testcontainers (`postgres:17-alpine`) en `BookingPersistenceAdapterTest` y `BookingCancellationIT` | **SÍ** |
| **RabbitMQ 4** | `spring-boot-starter-amqp` | **SÍ (Parcial)** | Testcontainers (`rabbitmq:4-management-alpine`) en `BookingCancellationIT`. Deshabilitado en `NotificationFlowIT`. En unit tests se usa mock `RabbitTemplate`. | **SÍ** |
| **MongoDB 7** | `spring-boot-starter-data-mongodb` | **SÍ (Audit)** | Testcontainers (`mongo:7`) en `audit-service/backend/.../AuditEventRepositoryTest`. En backend principal no aplica. | **SÍ** |
| **Apache Kafka** | `spring-kafka`, `spring-kafka-test` | **NO** | **Ninguna prueba activa ejecuta Kafka.** En `BookingCancellationIT` se desactiva explícitamente (`spring.kafka.listener.auto-startup=false`) y el publicador se sustituye por `@MockitoBean`. Cero pruebas de integración para Kafka. | NO |
| **Redis** | `spring-boot-starter-data-redis` | **NO** | **Ninguna prueba ejecuta Redis.** `OfferingCacheConsistencyTest` utiliza un stub en memoria (`InMemoryCache`) en vez de Redis Testcontainer o Redis Embedded. | NO |
| **Stripe API** | `stripe-java`, OpenFeign | **NO (Mocked)** | `StripePaymentAdapterTest` simula el cliente Feign con Mockito (`StripeFeignClient`). `StripeWebhookControllerTest` simula el servicio. No hay llamadas a `api.stripe.com`. | NO |
| **Google Gemini AI** | `spring-ai-starter-model-google-genai` | **NO (Mocked)** | `GeminiAiAdapterTest` usa Mockito para `ChatClient`. `GoogleGenAiChatAutoConfiguration` se excluye en `BookingCancellationIT`. No requiere API Key real. | NO |
| **OpenAI** | `spring-ai-starter-model-openai` | **NO (Mocked)** | `OpenAiAiAdapterTest` usa Mockito para `ChatClient`. Auto-configuración desactivada por defecto. | NO |
| **Groq Speech-to-Text** | `RestTemplate` HTTP Client | **NO (Mocked)** | `GroqSpeechToTextAdapterTest` usa mock de `RestTemplate` con clave simulada. No hay tráfico a la API de Groq. | NO |
| **ElevenLabs TTS** | `spring-boot-starter-web` | **NO** | No existen pruebas unitarias ni de integración para el adaptador TTS de ElevenLabs. | NO |
| **SMTP / Mail (Mailpit)** | `spring-boot-starter-mail` | **NO (Mocked)** | `EmailNotificationSenderTest` usa mock de `JavaMailSender`. No requiere conexión a servidor SMTP. | NO |

---

### 5.2. Variables de Entorno y Credenciales para Ejecución Local

Para levantar la suite o la aplicación completa localmente sin errores de inyección de configuración, se contemplan los siguientes parámetros (documentados sin valores sensibles):

- **Backend Principal:**
  - `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`: Conexión PostgreSQL (valores predeterminados locales: `jdbc:postgresql://localhost:5432/skillbridge`, `skillbridge`, `skillbridge_dev`).
  - `REDIS_URL`: Conexión Redis (`redis://localhost:6379`).
  - `RABBITMQ_URL`: Conexión RabbitMQ (`amqp://guest:guest@localhost:5672`).
  - `KAFKA_BOOTSTRAP_SERVERS`: Conexión Kafka (`localhost:9092`).
  - `JWT_SECRET`: Secreto criptográfico de al menos 32 caracteres para firma HMAC-SHA.
  - `GEMINI_API_KEY`, `OPENAI_API_KEY`, `GROQ_API_KEY`, `ELEVENLABS_API_KEY`: Credenciales de proveedores de IA externos (no requeridas para ejecutar pruebas unitarias gracias al aislamiento por mocks).
  - `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`: Credenciales de Stripe (no requeridas en tests por estar aislados con mocks).
- **Audit Service Backend:**
  - `AUDIT_MONGO_URI`: Cadena de conexión MongoDB (`mongodb://localhost:27017/audit`).
  - `KAFKA_BOOTSTRAP_SERVERS`: Broker Kafka (`localhost:9092`).
- **Frontend Principal:**
  - `API_URL`: Ruta relativa o absoluta hacia el backend (`/api`).
  - `STRIPE_PUBLISHABLE_KEY`: Clave pública de Stripe para inicialización del SDK (`pk_test_...`).

---

## 6. Examen de Integración Continua (CI/CD)

### 6.1. Workflow Principal: `.github/workflows/ci.yml`

El archivo [.github/workflows/ci.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/workflows/ci.yml) define las reglas de construcción y verificación automatizada en GitHub Actions:

```yaml
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]
```

- **Condiciones de Trigger:** Push o Pull Request dirigidos a las ramas `main` y `develop`. No se ejecuta en ramas de feature a menos que tengan un PR abierto hacia `main` o `develop`.
- **Permisos:** `contents: read` (principio de menor privilegio).

#### Análisis de Jobs:

1. **Job `backend`:**
   - **Runner:** `ubuntu-latest`.
   - **Java:** `setup-java@v4` con distribución `temurin`, versión `21` y caché `maven`.
   - **Comando de prueba y empaquetado:** `mvn -s ../.github/.maven/settings.xml -B clean verify` en el directorio `backend`.
   - **Comportamiento real en CI:**
     - En `ubuntu-latest`, el demonio de Docker está disponible de fábrica.
     - En la fase `test`, Surefire ejecuta las 35 clases `*Test.java` activas (incluyendo `BookingPersistenceAdapterTest`, que levanta `postgres:17-alpine` vía Testcontainers).
     - En la fase `integration-test`/`verify`, Failsafe ejecuta `BookingCancellationIT`, que levanta PostgreSQL 17 y RabbitMQ 4 vía Testcontainers.
     - Las pruebas deshabilitadas (`NotificationFlowIT` y `JpaOfferingRepositoryTest`) se saltan en silencio.
2. **Job `frontend`:**
   - **Runner:** `ubuntu-latest`.
   - **Node:** `setup-node@v4` con `node-version: '22'`, caché `npm` y ruta `frontend/package-lock.json`.
   - **Instalación:** `npm ci`.
   - **Pruebas:** `npm run test -- --watch=false --browsers=ChromeHeadless`.
   - **Build:** `npm run build` (ejecuta `prebuild` -> `node scripts/generate-env.mjs`, y compila con Angular).
3. **Job `docker`:**
   - **Dependencia:** `needs: [backend, frontend]`. Solo corre si ambos jobs previos fueron exitosos.
   - **Construcción de imágenes:**
     - `docker/build-push-action@v6` con contexto `./backend`, etiqueta `skillbridge-api:ci`, `push: false`.
     - `docker/build-push-action@v6` con contexto `./frontend`, etiqueta `skillbridge-web:ci`, `push: false`.

---

### 6.2. Brechas Críticas del Pipeline de CI

1. **Omisión Total de `audit-service`:**
   - Ningún job de `ci.yml` hace referencia a `audit-service/backend` ni `audit-service/frontend`.
   - El código de `audit-service` jamás se compila, no se prueban sus repositorios ni se construyen sus imágenes Docker en CI.
2. **Inexistencia de Gates de Calidad de Código y Linters:**
   - No hay paso de `npm run lint` en el job `frontend`.
   - No hay plugins ni pasos de linters de Java (Checkstyle, Spotless, PMD o SonarQube) en el job `backend`.
3. **Redundancia y Desperdicio de Recursos en Job `docker`:**
   - El job `docker` compila nuevamente el backend Java desde cero adentro del contenedor Docker (`mvn -B clean package -DskipTests`) y el frontend (`npm install && npm run build`), en lugar de aprovechar los artefactos (`.jar` y `dist/`) ya generados y validados por los jobs previos.
4. **Discrepancia en la Instalación de Dependencias de Frontend:**
   - En el job `frontend` de CI se ejecuta `npm ci`.
   - En el `frontend/Dockerfile` (construido en el job `docker`), la línea 4 ejecuta `RUN npm install --include=dev --no-audit --no-fund`. `npm install` no garantiza reproducibilidad estricta contra el lockfile como lo hace `npm ci`.
5. **Dependencia de Token Secreto en Workflow Qodana:**
   - En [.github/workflows/qodana_code_quality.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/workflows/qodana_code_quality.yml), se invoca `JetBrains/qodana-action@v2026.1` utilizando el secreto `QODANA_TOKEN_1948119795`. En Pull Requests provenientes de forks públicos, los secretos de GitHub Actions no se transmiten, provocando el fallo del workflow.

---

## 7. Verificaciones Locales de Bajo Riesgo Ejecutadas

De acuerdo con las reglas de auditoría (sin red, sin actualizar dependencias, sin modificar archivos rastreados y sin alterar el estado de git), se ejecutaron las siguientes comprobaciones locales de solo lectura:

```text
┌─────────────────────────────────────────────────────────────┬───────────┬────────────────────────────────────────────────────────┐
│ Comando Ejecutado                                           │ Exit Code │ Resultado / Diagnóstico                                │
├─────────────────────────────────────────────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ make -n up                                                  │ 2         │ FALLO: "Makefile:12: *** missing separator. Stop."     │
│                                                             │           │ Línea 12 no indentada bajo un target.                  │
├─────────────────────────────────────────────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ cd backend && mvn -o validate                               │ 0         │ ÉXITO: Descriptor backend/pom.xml válido en offline.   │
├─────────────────────────────────────────────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ cd audit-service/backend && mvn -o validate                 │ 0         │ ÉXITO: audit-service/backend/pom.xml válido en offline.│
├─────────────────────────────────────────────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ npm --prefix frontend ls --package-lock-only                │ 0         │ ÉXITO: Lockfile de frontend consistente e íntegro.     │
├─────────────────────────────────────────────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ npm --prefix audit-service/frontend ls --package-lock-only  │ 0         │ ÉXITO: Lockfile de audit-frontend íntegro.             │
└─────────────────────────────────────────────────────────────┴───────────┴────────────────────────────────────────────────────────┘
```

### Comandos recomendados NO ejecutados localmente para proteger el repositorio:

- `cd frontend && npm ci`: No ejecutado para evitar descargas de red o escrituras en `node_modules` no versionados durante la auditoría.
- `cd frontend && npm run build`: No ejecutado porque `prebuild` (`scripts/generate-env.mjs`) sobreescribe `public/env.js`, el cual es un archivo **rastreado en git**.
- `cd backend && mvn clean verify`: No ejecutado localmente para no alterar el árbol de trabajo ni descargar dependencias no cacheadas.
- `cd audit-service/backend && mvn clean`: **Peligro alto.** No ejecutado porque `audit-service/backend/target/` contiene binarios rastreados en git; ejecutar `clean` eliminaría archivos rastreados ensuciando el `git status`.

---

## 8. Registro de Hallazgos de Calidad, Build y DevOps (QA-001 a QA-012)

| ID | Hallazgo | Estado | Evidencia | Impacto Técnico | Siguiente Comprobación / Recomendación |
|---|---|---|---|---|---|
| **QA-001** | Error de sintaxis en `Makefile` impide cualquier ejecución de `make`. | **CONFIRMADO** | [Makefile#L9-L13](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/Makefile#L9-L13). Comando `make -n up` retornó exit code 2: `Makefile:12: *** missing separator. Stop.` | La orquestación básica (`make up`, `make build`, `make test-back`, `make test-front`) está completamente rota para desarrolladores y DevOps. | Eliminar o indentar la línea 12 `docker compose up --build -d` dentro del target `reset:` correspondiente. |
| **QA-002** | Ausencia de Maven Wrapper (`mvnw` / `mvnw.cmd`) en todo el repositorio. | **CONFIRMADO** | Búsqueda exhaustiva sin resultados. Scripts como [backend/test_kafka.sh#L3](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/test_kafka.sh#L3) instruyen `./mvnw clean verify`. | Rompe la reproducibilidad del build en máquinas locales que no tengan Maven preinstalado o tengan versiones incompatibles. | Generar el wrapper con `mvn wrapper:wrapper` y versionar `.mvn/wrapper/` y `mvnw*`. |
| **QA-003** | Monorepositorio fragmentado sin orquestador raíz de build ni multi-module POM. | **CONFIRMADO** | No existe `pom.xml` en raíz ni `package.json` raíz con workspaces. Existen cuatro proyectos desacoplados en submódulos. | Desarrolladores deben navegar carpeta por carpeta para compilar backend, frontend y audit-service. No hay compilación atómica del monorepo. | Crear un `pom.xml` padre que declare los módulos o estandarizar scripts de build monorepo. |
| **QA-004** | El pipeline de CI ignora por completo el subsistema `audit-service`. | **CONFIRMADO** | [.github/workflows/ci.yml#L12-L64](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.github/workflows/ci.yml#L12-L64) solo define jobs para `backend` y `frontend`. | Cambios rotos en `audit-service` (backend o frontend) pasarán el CI inadvertidos, permitiendo regresiones en producción. | Añadir matrices o jobs específicos en CI para compilar y probar `audit-service`. |
| **QA-005** | El repositorio git rastrea 53MB de binarios compilados en `audit-service/backend/target/`. | **CONFIRMADO** | `git ls-files audit-service/backend/target` devuelve `.jar`, `.class` y reportes XML/TXT rastreados. [.gitignore#L2](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/.gitignore#L2) solo ignora `backend/target/`. | Ejecutar `mvn clean` en audit-service ensucia git (`deleted: target/*.jar`). Aumenta innecesariamente el peso del clon del repo. | Ejecutar `git rm -r --cached audit-service/backend/target/` y actualizar `.gitignore` a `**/target/`. |
| **QA-006** | La fase de pruebas unitarias (`mvn test`) del backend está acoplada al demonio de Docker. | **CONFIRMADO** | [BookingPersistenceAdapterTest.java#L25-L36](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapterTest.java#L25-L36) usa Testcontainers con nombre `*Test.java`. | Surefire la ejecuta en `mvn test`. Si Docker no está corriendo o el desarrollador no tiene permisos en `/var/run/docker.sock`, el build unitario falla. | Renombrar la prueba a `BookingPersistenceAdapterIT.java` para que corra exclusivamente en la fase de integración (Failsafe). |
| **QA-007** | Pruebas de integración deshabilitadas por `"Docker Bug Testcontainers"`. | **CONFIRMADO** | [NotificationFlowIT.java#L56](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java#L56) y [JpaOfferingRepositoryTest.java#L16](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java#L16) tienen `@Disabled`. | Se omiten 5 pruebas automatizadas críticas de mensajería asíncrona RabbitMQ y consultas JPA a base de datos. | Corregir la inicialización de Testcontainers (timeouts o reutilización) y reactivar las pruebas. |
| **QA-008** | Inconsistencia en la instalación de dependencias en Dockerfile de frontend (`npm install` vs `npm ci`). | **CONFIRMADO** | [frontend/Dockerfile#L4](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/Dockerfile#L4) usa `npm install --include=dev`, mientras CI usa `npm ci`. | `npm install` puede modificar versiones flotantes resolviendo dependencias distintas a las del lockfile, comprometiendo builds reproducibles. | Cambiar la línea 4 de `frontend/Dockerfile` a `RUN npm ci`. |
| **QA-009** | Código fantasma y pruebas inexistentes documentadas como funcionales (`DemandAnalyticsService`). | **CONFIRMADO** | Documentado en [CHANGELOG.md#L13-L28](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/CHANGELOG.md#L13-L28), [RELEASE-NOTES-OCT-2026.md#L76-L132](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/RELEASE-NOTES-OCT-2026.md#L76-L132) y [scripts/demo-commands.sh#L43-L49](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/scripts/demo-commands.sh#L43-L49). Ni el servicio ni el test existen en el repo. | El script de demo disfraza el fallo con `|| true`. Se afirma falsamente que existen "25+ tests" y que pasan 104 tests. | Alinear la documentación con la realidad del código o restaurar los archivos fuente extraviados de la HU-22. |
| **QA-010** | Inexistencia de comprobaciones de linter y calidad de código en el pipeline principal de CI. | **CONFIRMADO** | [frontend/package.json](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/package.json) carece de script `lint` y de dependencias ESLint. En `ci.yml` no hay linter para Java ni TypeScript. | Errores de estilo, malas prácticas y violaciones arquitecturales no son detectados en PRs automáticos. | Integrar `@angular-eslint` en frontend y Checkstyle/Spotless en `backend/pom.xml`, agregando los steps en `ci.yml`. |
| **QA-011** | La compilación de frontend muta archivos rastreados en git (`public/env.js`). | **CONFIRMADO** | [frontend/scripts/generate-env.mjs#L17](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/scripts/generate-env.mjs#L17) sobreescribe `public/env.js`, el cual está rastreado en git (`git ls-files frontend/public/env.js`). | Ejecutar `npm run build` localmente genera modificaciones no deseadas en el árbol de trabajo (`git status` sucio). | Añadir `public/env.js` a `.gitignore` y versionar una plantilla `public/env.template.js`. |
| **QA-012** | Desconexión entre dependencias de infraestructura declaradas (Kafka, Redis) y pruebas automatizadas. | **CONFIRMADO** | Dependencias `spring-kafka` y `spring-boot-starter-data-redis` presentes en POM; [BookingCancellationIT.java#L76](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java#L76) apaga listeners de Kafka y usa stub para Redis. | No existe certeza automatizada de que la integración real con brokers Kafka o instancias de caché Redis funcione en producción. | Crear pruebas de integración con Testcontainers Kafka y Redis (`KafkaContainer`, `GenericContainer("redis:8-alpine")`). |

---

## 9. Limitaciones de la Auditoría y Próximos Pasos

### 9.1. Limitaciones Técnicas del Análisis
- **Aislamiento de red:** Por directriz estricta de auditoría, no se ejecutaron descargas de repositorios remotos de Maven Central ni del registro de npm.
- **No ejecución de contenedores Docker:** No se levantó el demonio Docker ni se instanciaron imágenes de PostgreSQL, RabbitMQ, Redis, Kafka ni MongoDB, por lo que la tasa de paso de las pruebas que dependen de Testcontainers se documenta con base en la configuración del código fuente y los reportes históricos del repositorio, sin afirmar que pasen en vivo en el commit actual.

### 9.2. Siguientes Comprobaciones Recomendadas (Fase de Remediación)
1. **Validación de `Makefile` tras corrección:** Aplicar corrección de indentación en línea 12 de `Makefile` y validar con `make -n up` y `make -n build`.
2. **Eliminación de artefactos de `audit-service/backend/target/` del índice de git:** Ejecutar en una rama dedicada de mantenimiento `git rm -r --cached audit-service/backend/target/` para sanear el repositorio.
3. **Reactivación de `NotificationFlowIT` y `JpaOfferingRepositoryTest`:** Investigar la causa raíz de la desconexión con Testcontainers y eliminar `@Disabled`.
4. **Incorporación de `audit-service` en `.github/workflows/ci.yml`:** Diseñar la matriz de build para asegurar cobertura total del monorepo.
