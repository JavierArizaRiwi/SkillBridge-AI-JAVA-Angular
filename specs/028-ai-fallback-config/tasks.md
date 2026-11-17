# Tasks: AI Fallback Configuration, Resilience & Observability (HU-28)

**Input**: Design documents from `specs/028-ai-fallback-config/` (`spec.md`, `plan.md`) and `docs/adr/0007-ai-provider-fallback-chain.md`.  
**Prerequisites**: `spec.md` (aprobado), `plan.md` (aprobado), HU-27 merged.  
**Branch**: `feature/HU-28-Configuracion,-resiliencia-y-observabilidad-del-fallback-de-IA`  
**Scope**: `backend/src/main/java/com/riwi/skillbridge/infrastructure/`, `backend/src/test/java/com/riwi/skillbridge/infrastructure/`, `backend/pom.xml`, `backend/src/main/resources/application.yml`, `.env.example`, `docker-compose.yml`, `docs/INTEGRATIONS.md`.

---

## Format: `[ID] [P?] [Story] Description`
- **[P]**: Tarea paralelizable (archivos independientes, sin dependencias mutuas)
- **[Story]**: Historia de usuario asociada (`[US1]`, `[US2]`, `[US3]`)

---

## Phase 1: Setup & Dependencies

**Purpose**: Incorporar la dependencia de Resilience4j en `backend/pom.xml` y asegurar compilación limpia.

- [x] T001 Añadir la dependencia `io.github.resilience4j:resilience4j-spring-boot3` (y módulos de micrometer si aplica) en `backend/pom.xml`.
- [x] T002 Verificar la resolución de dependencias y compilación mediante `mvn -f backend/pom.xml compile`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modelos de configuración tipada, parser de variables compactas y validador fail-fast.

- [x] T003 [P] Crear las clases de configuración tipada `AiFallbackProperties`, `CapabilityFallbackConfig`, `ChainElementConfig`, `CircuitBreakerProperties`, `RetryProperties` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/AiFallbackProperties.java` con prefijo `app.ai.fallback`.
- [x] T004 [P] Crear el modelo/record de estado de circuito `ProviderCircuitStatus` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/resilience/ProviderCircuitStatus.java`.
- [x] T005 Implementar la utilidad de normalización y parsing de sintaxis compacta (ej. `gemini:gemini-3.8-flash,openai:gpt-4o-mini`) tolerante a espacios en `AiFallbackProperties.java` o helper asociado.
- [x] T006 Implementar la lógica de retrocompatibilidad con `AI_PROVIDER` y `AI_AUDIO_PROVIDER` cuando no se configure una cadena explícita.

**Checkpoint**: Clases base y modelos inmutables compilando sin errores.

---

## Phase 3: User Story 1 - Configuración declarativa y validación al arranque (Priority: P1) 🎯 MVP

**Goal**: Permitir la configuración declarativa de cadenas por capacidad (`chat`, `vision`, `stt`, `tts`, `rag`, `embedding`) y validación temprana estricta al arranque (*fail-fast* ante cadena vacía, timeouts <= 0 o proveedores no reconocidos).

**Independent Test**: Ejecutar pruebas unitarias de binding y validación simulando configuraciones válidas, variables de entorno compactas y configuraciones erróneas que deben lanzar `IllegalStateException`.

### Tests for User Story 1 (TDD) ⚠️
- [x] T007 [US1] Escribir pruebas unitarias `AiFallbackPropertiesTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/config/AiFallbackPropertiesTest.java` verificando:
  - Escenario 1.1: Carga de valores por defecto válidos.
  - Escenario 1.2: Parsing de sintaxis compacta de variables de entorno (`AI_CHAT_FALLBACK_CHAIN`) con normalización de espacios.
  - Escenario 1.3: Retrocompatibilidad con `AI_PROVIDER` y `AI_AUDIO_PROVIDER`.
- [x] T008 [US1] Escribir pruebas unitarias `AiFallbackValidatorTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/config/AiFallbackValidatorTest.java` verificando:
  - Escenario 1.4: Falla inmediata al arranque cuando `timeoutSeconds <= 0`.
  - Escenario 1.5: Falla inmediata al arranque cuando una capacidad activa tiene su cadena vacía o sin proveedores habilitados.
  - Escenario 1.6: Falla inmediata al arranque ante un proveedor no registrado o desconocido.

### Implementation for User Story 1
- [x] T009 [US1] Implementar el validador fail-fast `AiFallbackValidator` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/AiFallbackValidator.java` validando integridad al arranque e impidiendo el boot con mensaje explicativo ante errores.
- [x] T010 [US1] Actualizar `backend/src/main/resources/application.yml` para registrar los valores por defecto de `app.ai.fallback` por capacidad.
- [x] T011 [US1] Conectar `AiFallbackProperties` y `AiFallbackValidator` en `AiFallbackConfiguration.java`.
- [x] T012 [US1] Ejecutar `mvn -f backend/pom.xml test -Dtest=AiFallbackPropertiesTest,AiFallbackValidatorTest` y confirmar que pasan todas las pruebas de US1.

**Checkpoint**: MVP de configuración tipada y validación fail-fast verificado de forma autónoma.

---

## Phase 4: User Story 2 - Aislamiento y resiliencia con Circuit Breaker (Priority: P2)

**Goal**: Aislar fallos repetidos de proveedores con Circuit Breakers independientes por `capacidad:proveedor:modelo` usando Resilience4j, saltando proveedores degradados en < 5 ms sin realizar llamadas a la red.

**Independent Test**: Registrar llamadas fallidas consecutivas hasta superar el umbral, verificar transición a `OPEN`, comprobar que la siguiente llamada se desvía inmediatamente al siguiente candidato en < 5 ms, y verificar transición a `HALF_OPEN` y `CLOSED` tras el periodo de enfriamiento.

### Tests for User Story 2 (TDD) ⚠️
- [x] T013 [US2] Escribir pruebas unitarias `AiCircuitBreakerManagerTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/resilience/AiCircuitBreakerManagerTest.java` verificando:
  - Escenario 2.1: Apertura del circuito al superar el umbral de fallos recuperables.
  - Escenario 2.2: Omitir proveedor con circuito abierto inmediatamente (en < 5 ms) sin invocar la red.
  - Escenario 2.3: Período de enfriamiento, prueba en HALF-OPEN y recuperación a CLOSED.
  - Escenario 2.4: Los errores no recuperables de validación de cliente no abren el circuito.
- [x] T014 [US2] Escribir pruebas de integración del Circuit Breaker en `FallbackAiRecommendationAdapterTest` simulando circuito abierto en primario y delegación inmediata al secundario.

### Implementation for User Story 2
- [x] T015 [US2] Implementar el gestor de resiliencia `AiCircuitBreakerManager` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/resilience/AiCircuitBreakerManager.java` encapsulando `CircuitBreakerRegistry` de Resilience4j por capacidad, proveedor y modelo.
- [x] T016 [US2] Integrar `AiCircuitBreakerManager` en los adaptadores compuestos de fallback (`FallbackAiRecommendationAdapter`, `FallbackVisionAdapter`, `FallbackSpeechToTextAdapter`, `FallbackTextToSpeechAdapter`) para omitir instantáneamente candidatos con circuito abierto.
- [x] T017 [US2] Ejecutar `mvn -f backend/pom.xml test -Dtest=AiCircuitBreakerManagerTest,FallbackAiRecommendationAdapterTest` y confirmar que todas las pruebas de resiliencia pasan.

**Checkpoint**: Circuit breaker operativo, con saltos rápidos (< 5 ms) y pruebas unitarias verdes.

---

## Phase 5: User Story 3 - Telemetría, endpoint admin y HealthIndicator (Priority: P3)

**Goal**: Exponer métricas estandarizadas en Micrometer, logs con `X-Correlation-Id` (sin secretos), endpoint administrativo seguro `GET /api/admin/ai/providers` (solo `ROLE_ADMIN`), y `HealthIndicator` agregado en Actuator (`UP`/`DEGRADED`).

**Independent Test**: Invocar endpoint administrativo con distintos roles y anónimos; verificar payload JSON sanitizado sin secretos; validar indicador de salud de Actuator en función del estado de los circuitos.

### Tests for User Story 3 (TDD) ⚠️
- [x] T018 [P] [US3] Escribir pruebas unitarias/MockMvc `AiAdminControllerTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/admin/AiAdminControllerTest.java` verificando:
  - Escenario 3.1: 401 Unauthorized para peticiones anónimas.
  - Escenario 3.2: 403 Forbidden para usuarios autenticados sin rol `ROLE_ADMIN` (ej. `ROLE_CUSTOMER`).
  - Escenario 3.3: 200 OK para `ROLE_ADMIN` retornando el detalle de capacidades, orden y estado de circuitos.
  - Escenario 3.4: Ausencia total de API keys, tokens o credenciales en la respuesta JSON.
- [x] T019 [P] [US3] Escribir pruebas unitarias `AiFallbackHealthIndicatorTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/health/AiFallbackHealthIndicatorTest.java` validando estado `UP` cuando todos los circuitos están cerrados y `DEGRADED` cuando algún circuito está abierto pero hay respaldos.

### Implementation for User Story 3
- [x] T020 [P] [US3] Extender `AiFallbackMetricsRecorder` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/metrics/AiFallbackMetricsRecorder.java` con el contador `ai.fallback.provider.used` y registro de estados de circuitos con Micrometer.
- [x] T021 [US3] Implementar DTO de respuesta `AiProvidersStatusResponse` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/admin/dto/AiProvidersStatusResponse.java` garantizando que no expone ningún secreto.
- [x] T022 [US3] Implementar el controlador REST `AiAdminController` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/admin/AiAdminController.java` con ruta `/api/admin/ai/providers` protegido con `@PreAuthorize("hasRole('ADMIN')")`.
- [x] T023 [US3] Implementar el `AiFallbackHealthIndicator` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/health/AiFallbackHealthIndicator.java` integrando con Actuator sin divulgar secretos internos.
- [x] T024 [US3] Ejecutar `mvn -f backend/pom.xml test -Dtest=AiAdminControllerTest,AiFallbackHealthIndicatorTest` y verificar paso de pruebas.

**Checkpoint**: Observabilidad completa, endpoint administrativo seguro y salud en Actuator verificados.

---

## Phase 6: Documentation & Environment Alignment

**Purpose**: Documentar las nuevas variables de entorno y configuración con valores ficticios y placeholders seguros.

- [x] T025 Actualizar `.env.example` con las variables de configuración de fallback (`AI_CHAT_FALLBACK_CHAIN`, `AI_VISION_FALLBACK_CHAIN`, `AI_STT_FALLBACK_CHAIN`, etc.) usando placeholders seguros y modelos verificados.
- [x] T026 Actualizar `docker-compose.yml` para mapear las nuevas variables opcionales de fallback al servicio `backend`.
- [x] T027 Actualizar `docs/INTEGRATIONS.md` con la sección de configuración de fallback declarativa, parámetros de circuit breaker y observabilidad.
- [x] T028 Ejecutar suite completa de tests de IA del backend (`mvn -f backend/pom.xml test -Dtest=*Ai*,*Fallback*`) y verificar que todos los tests pasan con 0 fallos.
