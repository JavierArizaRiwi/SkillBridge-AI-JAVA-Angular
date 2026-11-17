# Tasks: AI Model Fallback Chain (HU-27)

**Input**: Design documents from `specs/027-ai-model-fallback/` (`spec.md`, `plan.md`) and `docs/adr/0007-ai-provider-fallback-chain.md`.  
**Prerequisites**: `spec.md` (aprobado), `plan.md` (aprobado).  
**Branch**: `feature/HU-27-Modelos-de-IA-de-respaldo-(fallback-multi-proveedor)`  
**Scope**: `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/`, `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/`, `docs/adr/0007-ai-provider-fallback-chain.md`, `docs/INTEGRATIONS.md`.

---

## Format: `[ID] [P?] [Story] Description`
- **[P]**: Tarea paralelizable (archivos independientes, sin dependencias mutuas)
- **[Story]**: Historia de usuario asociada (`[US1]`, `[US2]`, `[US3]`)

---

## Phase 1: Setup & Data Structures

**Purpose**: Estructuras inmutables y registros base para la ejecución de la cadena de fallback.

- [x] T001 [P] Crear el modelo inmutable `AiModelCandidate` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/candidate/AiModelCandidate.java` con proveedor, modelo, orden de prioridad, timeout en segundos y estado de configuración.
- [x] T002 [P] Crear el modelo de resultado de intento `CandidateExecutionResult` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/candidate/CandidateExecutionResult.java` con candidato, resultado (éxito/fallo), tipo de error recuperable/no recuperable y latencia en milisegundos.
- [x] T003 [P] Crear el enum de clasificación `ErrorClassification` (RECOVERABLE, NON_RECOVERABLE) en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/classification/ErrorClassification.java`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Clasificación tipada de errores basada en tipos de excepción y códigos HTTP (sin regex ni inspección de texto).

- [x] T004 [P] Crear pruebas unitarias exhaustivas `AiErrorClassifierTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/classification/AiErrorClassifierTest.java` verificando clasificación de Timeout, HTTP 429, 5xx, JSON parsing de LLM, HTTP 400 y errores de validación de cliente.
- [x] T005 Implementar el clasificador de errores tipado `AiErrorClassifier` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/classification/AiErrorClassifier.java` resolviendo las pruebas de T004 sin inspección de cadenas de texto arbitrarias.
- [x] T006 Adaptar `GeminiAiAdapter` y `OpenAiAiAdapter` para delegar su clasificación en `AiErrorClassifier` y asegurar la aplicación efectiva de timeout por candidato.

**Checkpoint**: Base fundacional de clasificación tipada lista y verificada con pruebas unitarias verdes.

---

## Phase 3: User Story 1 - Conmutación transparente en recomendaciones (Priority: P1) 🎯 MVP

**Goal**: Proteger la funcionalidad central de recomendaciones mediante un adaptador compuesto `FallbackAiRecommendationAdapter` que conmuta automáticamente ante fallos recuperables.

**Independent Test**: Simular error 429 / 503 / timeout en primario y comprobar que `POST /api/ai/recommendations` resuelve con HTTP 200 usando el candidato secundario sin alterar contratos.

### Tests for User Story 1 (TDD) ⚠️
- [x] T007 [US1] Escribir pruebas unitarias `FallbackAiRecommendationAdapterTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/fallback/FallbackAiRecommendationAdapterTest.java` cubriendo:
  - Escenario 1.1: Primario saludable responde exitosamente (secundario no se invoca).
  - Escenario 1.2: Primario falla recuperable (HTTP 429 / timeout) y secundario responde exitosamente.
  - Escenario 1.3: Conmutación intra-proveedor (Gemini primario a Gemini secundario) antes de conmutación inter-proveedor (OpenAI).
  - Escenario 1.4: Error no recuperable del cliente (validación / 4xx propio) propaga excepción de inmediato sin activar respaldo.

### Implementation for User Story 1
- [x] T008 [US1] Implementar el adaptador compuesto `FallbackAiRecommendationAdapter` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/fallback/FallbackAiRecommendationAdapter.java` implementando `AiRecommendationPort` con `@Primary`.
- [x] T009 [US1] Configurar beans y resolución de candidatos de chat en `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/AiFallbackConfiguration.java`, permitiendo la coexistencia de adaptadores de Gemini y OpenAI sin bloqueo por `@ConditionalOnProperty`.
- [x] T010 [US1] Ejecutar `mvn test -Dtest=FallbackAiRecommendationAdapterTest` para verificar que todos los escenarios de US1 pasan.

**Checkpoint**: MVP de User Story 1 completado y verificado de forma autónoma.

---

## Phase 4: User Story 2 - Resiliencia multimodal en Visión y Voz (Priority: P2)

**Goal**: Extender el mecanismo de fallback a las capacidades multimodales de transcripción (STT), visión y síntesis de voz (TTS).

**Independent Test**: Simular fallas recuperables en `GroqSpeechToTextAdapter`, `GeminiVisionAdapter` y `ElevenLabsTextToSpeechAdapter`, verificando que conmutan transparentemente a los adaptadores secundarios.

### Tests for User Story 2 (TDD) ⚠️
- [x] T011 [P] [US2] Escribir pruebas unitarias `FallbackSpeechToTextAdapterTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/fallback/FallbackSpeechToTextAdapterTest.java` (Groq STT -> Gemini STT).
- [x] T012 [P] [US2] Escribir pruebas unitarias `FallbackVisionAdapterTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/fallback/FallbackVisionAdapterTest.java` (Gemini Vision primario -> modelo alternativo).
- [x] T013 [P] [US2] Escribir pruebas unitarias `FallbackTextToSpeechAdapterTest` en `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/fallback/FallbackTextToSpeechAdapterTest.java` (ElevenLabs TTS -> fallback/degradación).

### Implementation for User Story 2
- [x] T014 [US2] Implementar `FallbackSpeechToTextAdapter` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/fallback/FallbackSpeechToTextAdapter.java` implementando `SpeechToTextPort` con `@Primary`.
- [x] T015 [US2] Implementar `FallbackVisionAdapter` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/fallback/FallbackVisionAdapter.java` implementando `VisionPort` con `@Primary`.
- [x] T016 [US2] Implementar `FallbackTextToSpeechAdapter` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/fallback/FallbackTextToSpeechAdapter.java` implementando `TextToSpeechPort` con `@Primary`.
- [x] T017 [US2] Registrar la configuración de candidatos multimodales en `AiFallbackConfiguration.java`.
- [x] T018 [US2] Ejecutar las pruebas unitarias multimodales (`FallbackSpeechToTextAdapterTest`, `FallbackVisionAdapterTest`, `FallbackTextToSpeechAdapterTest`).

**Checkpoint**: Capacidades multimodales resilientes y verificadas.

---

## Phase 5: User Story 3 - Diagnóstico, telemetría y respuesta controlada ante agotamiento (Priority: P3)

**Goal**: Garantizar observabilidad (métricas Micrometer por intento, evento enriquecido con provider/model) y respuesta estricta HTTP 503 ProblemDetail sin mocks ante agotamiento total de la cadena.

**Independent Test**: Simular caída total de todos los candidatos y verificar HTTP 503 sin datos inventados; verificar emisión de métricas y metadata en eventos.

### Tests for User Story 3 (TDD) ⚠️
- [x] T019 [US3] Escribir pruebas unitarias en `FallbackAiRecommendationAdapterTest` y `AiFallbackMetricsRecorderTest` para:
  - Escenario 3.1: Agotamiento de toda la cadena lanza `AiProviderException(UNAVAILABLE)` mapeada a HTTP 503 sin emitir mocks.
  - Escenario 3.2: Registro de métricas por intento (`ai.fallback.attempts`) con tags adecuadas y latencia.
  - Escenario 3.3: Exclusión segura de candidatos sin API key configurada al inicializar (emite log WARN sin registrar la clave).

### Implementation for User Story 3
- [x] T020 [P] [US3] Implementar `AiFallbackMetricsRecorder` en `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/metrics/AiFallbackMetricsRecorder.java` integrando con `MeterRegistry`.
- [x] T021 [US3] Enriquecer el evento de negocio `RecommendationGeneratedPayload` en `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/RecommendationGeneratedPayload.java` con el campo `provider` (manteniendo compatibilidad con constructores existentes).
- [x] T022 [US3] Propagar la metadata del candidato resolutor (`provider`, `model`) y `X-Correlation-Id` en logs (MDC) y en la publicación del evento en `AiRecommendationService.java`.
- [x] T023 [US3] Implementar la validación y exclusión en el arranque de candidatos sin credenciales con log WARN sin divulgar secretos en `AiFallbackConfiguration.java`.

**Checkpoint**: Observabilidad completa, exclusión segura de credenciales y garantía de 503 estricto sin mocks.

---

## Phase 6: Documentación & Verificación Final

**Purpose**: Cierre documental, sincronización de contratos y validación general de la suite.

- [x] T024 [P] Actualizar estado de `docs/adr/0007-ai-provider-fallback-chain.md` de *Proposed* a *Accepted*.
- [x] T025 [P] Actualizar `docs/INTEGRATIONS.md` con la arquitectura de la cadena de fallback, precedencia de candidatos y manejo de fallos.
- [x] T026 Ejecutar suite de pruebas completa de AI (`mvn test -Dtest="*Ai*Test,*Fallback*Test,*Speech*Test"`) y compilar con `mvn test-compile`.
