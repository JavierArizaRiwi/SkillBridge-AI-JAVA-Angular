# Evidencia de Auditoría: Mapa de Migración y Extracción Operativa de README.md

**Destino de evidencia:** `docs/audit/evidence/06-documentation-migration.md`  
**Rol del auditor:** Ingeniero de Documentación Técnica y Arquitecto de Software  
**Documento fuente auditado:** [README.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/README.md) (2.021 líneas)  
**Estado de README.md:** Preservado íntegramente sin truncamiento ni reemplazo en este ciclo.

---

## 1. Justificación y Alcance de la Extracción

El archivo `README.md` original cuenta con 2.021 líneas que combinan una guía docente académica, tutoriales paso a paso de servicios cloud gratuitos de terceros (Neon, Upstash, CloudAMQP, Render, Vercel), manuales de operación de Docker, documentación de arquitectura hexagonal y registros de comandos.

Para permitir que agentes de IA (Claude Code, Antigravity) y desarrolladores humanos naveguen eficientemente sin consumir decenas de miles de tokens en cada consulta, la información técnica y operativa fue extraída y consolidada en documentos modulares especializados.

---

## 2. Matriz de Migración de Secciones

| Sección Original en `README.md` | Líneas Aprox. | Temas Principales Cubiertos | Documento(s) de Destino | Estado de la Migración |
|---|---|---|---|---|
| **1. Qué van a aprender** | 11–46 | Objetivos pedagógicos de arquitectura hexagonal y microservicios | [docs/PROJECT_CONTEXT.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/PROJECT_CONTEXT.md) | Migrado a contexto general. |
| **2. Caso de uso y Flujos** | 47–172 | Descripción de SkillBridge, flujo de catálogo, reservas e IA | [docs/PROJECT_CONTEXT.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/PROJECT_CONTEXT.md), [docs/architecture/overview.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/architecture/overview.md) | Consolidado con diagramas de flujo. |
| **3. Arquitectura general** | 173–233 | Diagrama ASCII general, flujo HTTP y desacoplamiento de eventos | [docs/architecture/overview.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/architecture/overview.md) | Migrado y modelado en Mermaid. |
| **4. Arquitectura Hexagonal** | 234–298 | Regla de dependencias, adaptadores in/out, puertos | [docs/architecture/overview.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/architecture/overview.md), [docs/MODULES/backend.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/MODULES/backend.md) | Consolidado con clases concretas. |
| **5. Tecnologías (Stack)** | 299–391 | 12 servicios Docker, versiones de Java, Angular, Kafka, Mongo | [docs/PROJECT_CONTEXT.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/PROJECT_CONTEXT.md), [docs/INTEGRATIONS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/INTEGRATIONS.md) | Verificado con docker-compose real. |
| **6. Requisitos locales** | 392–411 | Docker, Node 22, Java 21, cURL | [CLAUDE.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/CLAUDE.md), [AGENTS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/AGENTS.md), [docs/TESTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/TESTING.md) | Sintetizado para agentes. |
| **7. Ejecución con Docker Compose** | 412–510 | Comandos `docker compose up`, verificación y tabla de URLs | [CLAUDE.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/CLAUDE.md), [docs/INTEGRATIONS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/INTEGRATIONS.md) | Extraído íntegro con puertos locales. |
| **8. Primer recorrido funcional** | 511–651 | Pruebas de endpoints cURL (auth, reservas, IA) | [docs/CONTRIBUTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/CONTRIBUTING.md), [docs/INTEGRATIONS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/INTEGRATIONS.md) | Migrado como guía de tracing. |
| **9. RabbitMQ: retry + DLQ** | 652–696 | Parámetros de retry, queues, dead-letter | [docs/INTEGRATIONS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/INTEGRATIONS.md) | Verificado contra `RabbitConfiguration.java`. |
| **10. IA y Proveedores** | 697–920 | Abstracción Gemini/OpenAI, Groq STT, ElevenLabs TTS, Stripe, Mailpit | [docs/INTEGRATIONS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/INTEGRATIONS.md), [docs/MODULES/backend.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/MODULES/backend.md) | Consolidado con ADR-001 y propiedades reales. |
| **11. Nginx** | 921–967 | Template de Nginx, reverse proxy `/api/` y SPA routing | [docs/MODULES/frontend.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/MODULES/frontend.md) | Verificado con `nginx.conf.template`. |
| **12. Observabilidad** | 968–1022 | Actuator, Micrometer Prometheus scraping, Grafana | [docs/INTEGRATIONS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/INTEGRATIONS.md) | Verificado con `ops/`. |
| **13–14. Ejecución sin Docker** | 1023–1065| Ejecutar Spring Boot (`mvn spring-boot:run`) y Angular (`ng serve`) | [docs/MODULES/backend.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/MODULES/backend.md), [docs/MODULES/frontend.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/MODULES/frontend.md) | Migrado a guías de módulos. |
| **15. Tests** | 1066–1093| Tests unitarios y Testcontainers | [docs/TESTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/TESTING.md) | Enriquecido con análisis de `@Disabled`. |
| **16. CI con GitHub Actions & Qodana**| 1094–1125| Descripción de workflows `ci.yml` y `qodana` | [docs/TESTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/TESTING.md), [docs/CONTRIBUTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/CONTRIBUTING.md) | Cruzado con auditoría de build. |
| **17–24, 36. Despliegue Cloud Gratuito** | 1126–1461, 1951–1972 | Tutoriales paso a paso de Neon, Upstash, CloudAMQP, Render, Vercel | [docs/DEPLOYMENT-CHECKLIST.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/DEPLOYMENT-CHECKLIST.md), [docs/adr/0002-managed-services-in-cloud.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/adr/0002-managed-services-in-cloud.md) | Conservado para referencia de despliegue. |
| **25. Variables de entorno** | 1462–1520| Diccionario de variables de producción y locales | [docs/INTEGRATIONS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/INTEGRATIONS.md), [docs/MODULES/backend.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/MODULES/backend.md), [docs/MODULES/audit-service.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/MODULES/audit-service.md) | Tablas normalizadas por módulo. |
| **26. Seguridad** | 1521–1549| Roles, JWT, contraseñas, mejoras sugeridas | [docs/SECURITY-MODEL.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/SECURITY-MODEL.md) | Contrastado con hallazgos de seguridad. |
| **27. Manejo de errores** | 1550–1574| RFC 7807 ProblemDetail y códigos HTTP | [docs/architecture/overview.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/architecture/overview.md), [docs/CONTRIBUTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/CONTRIBUTING.md) | Verificado con `GlobalExceptionHandler.java`. |
| **28. Migraciones** | 1575–1614| Flyway naming conventions y ejecución | [docs/MODULES/backend.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/MODULES/backend.md), [docs/CONTRIBUTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/CONTRIBUTING.md) | Documentado de V1 a V7. |
| **29–32, 35. Metodología de Células** | 1615–1771, 1917–1950| Git Flow, Definition of Done, Roadmap N1-N5, Antipatrones | [docs/CONTRIBUTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/CONTRIBUTING.md) | Consolidado en guía de contribución. |
| **33–34. Comandos & Troubleshooting** | 1772–1916| Puertos en uso, fallos de conexión Postgres/Redis/Rabbit/IA, CORS | [docs/INTEGRATIONS.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/INTEGRATIONS.md), [docs/SECURITY-MODEL.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/SECURITY-MODEL.md), [CLAUDE.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/CLAUDE.md) | Integrado en secciones de resolución operativa. |
| **37–38. Archivos complementarios y Fin**| 1973–2021| Referencias a `docs/` y licencia | [docs/PROJECT_CONTEXT.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/PROJECT_CONTEXT.md) | Consolidado en índice central. |

---

## 3. Discrepancias y Asuntos No Resueltos

Durante la extracción se identificaron los siguientes aspectos que deben mantenerse bajo observación:

1. **Vencimiento de Tiers Gratuitos de Terceros (Fecha de corte: 29-09-2026)**:
   - Las instrucciones para desplegar en Render Free, Neon, Upstash y CloudAMQP dependen de políticas comerciales de proveedores SaaS que pueden suspender o limitar contenedores tras periodos de inactividad (ej. Render cold-starts de 50 segundos).
2. **Código Fantasma en Script de Demostración (`QA-009`)**:
   - `scripts/demo-commands.sh:49` intenta invocar `DemandAnalyticsService`, clase que no existe en el repositorio (enmascarado con `|| true`).
3. **Discrepancia de Tokens entre Frontend y Audit Service (`SEC-004`)**:
   - `README.md` describe un modelo unificado de autenticación; sin embargo, `frontend/` utiliza cookies HttpOnly mientras que `audit-service/frontend` depende de `localStorage.getItem('skillbridge_token')`.

---

## 4. Recomendación para la Futura Reducción de README.md

En una fase de revisión posterior, se recomienda sustituir el contenido de [README.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/README.md) por una página de inicio concisa (menor a 200 líneas) que contenga:
- Título y propósito principal de SkillBridge AI.
- Requisitos mínimos de software (Docker, Node 22, Java 21).
- Guía de inicio rápido en 3 comandos (`docker compose up --build -d`).
- Tabla de URLs y puertos locales principales.
- Enlaces directos a la nueva suite documental ([CLAUDE.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/CLAUDE.md), [docs/PROJECT_CONTEXT.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/PROJECT_CONTEXT.md), [docs/CONTRIBUTING.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/CONTRIBUTING.md), etc.).
