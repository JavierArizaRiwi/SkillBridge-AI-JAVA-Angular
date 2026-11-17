# Quality Checklist: AI Model Fallback Specification (HU-27 / SPEC-027)

Este checklist valida la calidad, completitud y alineación arquitectónica de la especificación SDD documentada en `specs/027-ai-model-fallback/spec.md`.

---

## 1. Spec Quality & Best Practices

- [x] **Enfoque en el *Qué* y el *Por Qué* (Sin detalles prematuros de implementación)**: La especificación define el comportamiento de la cadena, las condiciones de conmutación y los resultados esperados sin imponer nombres de clases internas, algoritmos de bajo nivel ni detalles ajenos a las restricciones explícitas del issue.
- [x] **Requisitos Funcionales Verificables**: Cada requisito (FR-001 a FR-010) sigue la formulación normativa "El sistema DEBE...", con criterios de éxito objetivos y contrastables mediante pruebas.
- [x] **Criterios de Éxito Medibles y Agnósticos**: Los criterios de éxito (SC-001 a SC-007) son cuantitativos (porcentajes de éxito, umbrales de latencia en milisegundos, cumplimiento de estándares) y no dependen de detalles de frameworks.
- [x] **Alcance Acotado y Fronteras Claras**: El alcance distingue estrictamente entre la lógica de la cadena de fallback (esta especificación HU-27) y la configuración avanzada/circuit breaker (dependencia HU-28).
- [x] **Historias de Usuario Priorizadas e Independientes**: Las tres historias (P1, P2, P3) cuentan con justificación de prioridad y pruebas independientes. La historia P1 por sí sola constituye un MVP funcional desplegable.
- [x] **Escenarios de Aceptación Exhaustivos**: Cada historia incluye escenarios numerados en formato Given/When/Then cubriendo caminos felices, conmutaciones por error y condiciones de rechazo.
- [x] **Casos de Borde (Edge Cases) Documentados**: Se detallan situaciones críticas como credenciales faltantes en el arranque, respuestas corruptas o JSON truncado de LLMs, timeouts acumulados y errores 4xx del cliente.
- [x] **Fundamentación en Código Real**: Se citan archivos y líneas exactas con marcadores `[VERIFIED]` para hechos comprobados y `[INFERRED]` para deducciones arquitectónicas.
- [x] **Límite de Clarificaciones de Negocio**: Se incluyen exactamente 2 marcadores `[NEEDS CLARIFICATION]` (dentro del límite de máximo 3), cada uno con opciones formuladas y recomendación explícita.
- [x] **Restricción de Alcance de Archivos**: Solo se crean y modifican archivos dentro del directorio autorizado `specs/027-ai-model-fallback/`.

---

## 2. Issue #78 Acceptance Criteria Traceability

- [x] **Spec SDD en `specs/027-ai-model-fallback/`**: Estructura homologada con `spec-template.md` de GitHub Spec Kit generada antes de la fase de implementación.
- [x] **Adaptador compuesto por puerto de IA**: Cubierto en Historias 1 y 2, FR-001, FR-002, SC-001, y SC-003. Los servicios de aplicación permanecen inalterados.
- [x] **Conmutación exclusiva ante errores recuperables**: Cubierto en Historias 1 y 2, Edge Cases 2 y 4, FR-002, FR-003, SC-001, y SC-002 (errores 4xx propios no conmutan).
- [x] **Soporte de múltiples modelos del mismo proveedor y entre proveedores**: Cubierto en Historia 1 (Escenario 1.3), FR-004, y SC-001.
- [x] **Omisión segura de proveedores sin API key en el arranque**: Cubierto en Historia 3 (Escenario 3.3), Edge Case 1, FR-005, y SC-006 (log WARN sin divulgar claves).
- [x] **Respuesta controlada HTTP 503 ante agotamiento total (cero mocks)**: Cubierto en Historia 3 (Escenario 3.1), Edge Case 5, FR-006, y SC-007.
- [x] **Metadatos en evento `RecommendationGenerated` y métricas por intento**: Cubierto en Historia 3 (Escenario 3.2), FR-007, FR-008, y SC-005.
- [x] **Verificabilidad mediante pruebas unitarias sin red ni claves**: Cubierto en Historias 1, 2 y 3 (Independent Test), FR-009, y SC-001.
- [x] **Registro y actualización de documentación arquitectónica (ADR-001 / ADR)**: Cubierto en FR-010 y Assumptions.

---

## 3. Hexagonal & Non-Destructive Principles

- [x] **Inviolabilidad del Dominio Puro**: No se introducen dependencias de infraestructura ni frameworks dentro del paquete de dominio.
- [x] **Contratos de Puertos Preservados**: Las interfaces `AiRecommendationPort`, `VisionPort`, `SpeechToTextPort` y `TextToSpeechPort` no sufren modificaciones de firma incompatibles.
- [x] **Sin Cambios de Código Prematuros**: La fase actual es exclusivamente `specify`. No se modifican archivos de código Java, TypeScript ni configuraciones de producción.
- [x] **Sin Comisiones ni Mutación de Git**: No se ejecutan comandos destructivos de git (`git commit`, `git add`, `git reset`, etc.).
