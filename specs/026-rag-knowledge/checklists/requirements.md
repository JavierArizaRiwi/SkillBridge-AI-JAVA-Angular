# Specification Quality Checklist: HU-26 Asistente de Conocimiento con RAG

**Feature**: `specs/026-rag-knowledge/spec.md`  
**Target Branch**: `feature/026-rag-knowledge`  
**Issue Reference**: [#77](https://github.com/Danilo-Doria/SkillBridge-AI-JAVA-Angular/issues/77)  
**SDD Phase**: specify (fase de especificación funcional)  

---

## 1. Calidad y Estructura de Historias de Usuario

- [x] **Estructura estándar respetada**: La especificación sigue la estructura exacta de GitHub Spec Kit (`spec-template.md`).
- [x] **Priorización clara de historias**: Las historias cuentan con prioridades explícitas (P1, P2, P3).
- [x] **MVP autónomo e independiente**: La historia User Story 1 (P1) define un Producto Mínimo Viable completamente funcional y verificable por sí sola.
- [x] **Justificación de prioridad documentada**: Cada historia incluye la sección explícita `**Why this priority**`.
- [x] **Prueba independiente por historia**: Cada historia detalla la sección `**Independent Test**` que describe cómo validar la funcionalidad de forma aislada.
- [x] **Escenarios de aceptación exhaustivos**: Todos los escenarios de aceptación están redactados bajo el formato estructurado **Given / When / Then** numerado.
- [x] **Casos límite (Edge Cases) cubiertos**: Se contemplan escenarios anómalos (entradas vacías o >500 caracteres, usuarios anónimos, prompt injection, caída de LLMs, concurrencia).

---

## 2. Requisitos Funcionales y Abstracción Arquitectónica

- [x] **Enfoque en el *qué* y el *por qué***: La especificación evita decisiones de implementación prematuras, respetando exclusivamente las restricciones de diseño funcional fijadas en el issue.
- [x] **Requisitos verificables y no ambiguos**: Cada requisito funcional (FR-001 a FR-013) está redactado como "El sistema DEBE ..." con criterios de cumplimiento objetivos.
- [x] **Respeto a la arquitectura hexagonal**: Se especifican los límites arquitectónicos (`AskKnowledgeUseCase` como puerto de entrada, `KnowledgeSearchPort` y puerto LLM como puertos de salida, y dominio sin dependencias de frameworks).
- [x] **Ingesta de datos estrictamente pública**: Se prohíbe de forma explícita en los requisitos la indexación de datos privados (reservas, pagos, usuarios) y documentos internos (`docs/audit/**`, `RISK-REGISTER.md`).
- [x] **Validación de fuentes citadas**: Se define como requisito funcional obligatorio que el arreglo `sources` devuelto solo contenga fragmentos efectivamente citados por la respuesta del LLM.
- [x] **Prohibición de mocks silenciosos**: Se prohíbe categóricamente en los requisitos el respaldo automático a mocks con precios o textos fijos en producción.
- [x] **Aplicación real de timeout**: Se exige que el timeout del proveedor de IA se aplique a nivel de socket/cliente HTTP o ejecución asíncrona, prohibiendo la detección por texto de error.

---

## 3. Criterios de Éxito y Mediciones

- [x] **Criterios medibles cuantitativamente**: Los criterios de éxito (SC-001 a SC-007) definen umbrales porcentuales (100%, 0%) o de tiempo de respuesta (P95 < 50 ms).
- [x] **Agnósticos de tecnologías específicas**: Las métricas evalúan el comportamiento observable desde la perspectiva del usuario y de la API, sin acoplarse a librerías internas.
- [x] **Verificación sin costos externos**: Se garantiza que la suite de pruebas del módulo de conocimiento pueda ejecutarse íntegramente en local con `mvn test` sin requerir claves de API de pago ni conectividad externa.

---

## 4. Fundamentación en el Código y Trazabilidad

- [x] **Evidencia contrastada con el repositorio**: Se verificaron los archivos y líneas reales del proyecto (`SecurityConfiguration.java`, `GlobalExceptionHandler.java`, `OfferingRepositoryPort.java`, `CategoryRepositoryPort.java`, `BookingCancellationPolicy.java`, `pom.xml`, `ai.component.ts`).
- [x] **Marcas explícitas de certeza**: Todas las afirmaciones en la sección Assumptions están etiquetadas formalmente como `[VERIFIED]` o `[INFERRED]`.
- [x] **Marcadores de clarificación delimitados**: Se incluyen exactamente 3 marcadores `[NEEDS CLARIFICATION: …]`, cada uno enfocado en decisiones reales de producto/negocio y acompañado de opciones propuestas.
- [x] **Trazabilidad 100% con Criterios del Issue #77**: Cada uno de los 14 criterios de aceptación descritos en el issue tiene correspondencia directa con historias de usuario, requisitos funcionales (FR) o criterios de éxito (SC).

---

## 5. Alcance y Dependencias

- [x] **Dependencias identificadas**: Se declaran las dependencias hacia Spring AI 1.1.8, la cadena de fallbacks (HU-27/HU-28) y la autenticación JWT.
- [x] **Límites de alcance explícitos (*Out of Scope*)**: Se excluyen taxativamente bases vectoriales (pgvector), embeddings externos de pago, historial multi-turno y rate limiting distribuido.
