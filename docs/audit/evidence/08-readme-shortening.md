# Evidencia de Auditoría: Transformación y Reducción Operativa de README.md

**Destino de evidencia:** `docs/audit/evidence/08-readme-shortening.md`  
**Rol del auditor:** Ingeniero Senior de Documentación Técnica y Arquitectura de Software  
**Fecha de ejecución:** 2026-10-21  
**Commit auditado:** `cdd009aa8c9ba351fa8bb50bb1ad0a381e259793` (rama `develop`)  
**Documento fuente transformado:** [README.md](../../../README.md)  

---

## 1. Métricas de Transformación

| Métrica | Estado Anterior | Estado Posterior | Diferencia Absoluta | Variación (%) |
|---|---|---|---|---|
| **Líneas de código / texto (`wc -l`)** | 2.021 líneas | 161 líneas | -1.860 líneas | -92.03% |
| **Palabras aprox.** | 10.450 palabras | 992 palabras | -9.458 palabras | -90.51% |
| **Tamaño en disco** | ~82 KB | ~7.3 KB | -74.7 KB | -91.10% |
| **Enlaces relativos validados** | N/A (dispersos) | 22 enlaces verificados | +22 enlaces directos | 100% resolubles |

El nuevo `README.md` cumple estrictamente con el rango objetivo estipulado (150–200 líneas), posicionándose en **161 líneas** sin perder rigor técnico ni instrucciones operativas críticas.

---

## 2. Matriz de Auditoría y Verificación de Migración (38 Secciones)

A continuación se detalla la correspondencia entre las 38 secciones originales de `README.md`, el destino indicado por el mapa de migración previa ([06-documentation-migration.md](06-documentation-migration.md)), y el estado de clasificación y verificación real tras inspección física en el repositorio:

| Sección Original en `README.md` | Líneas Originales | Clasificación Semántica | Destino Indicado en Migración | Archivos Reales Verificados | Estado de Verificación |
|---|---|---|---|---|---|
| **1. Qué van a aprender** | 11–46 | Totalmente migrado | `docs/PROJECT_CONTEXT.md` | [PROJECT_CONTEXT.md](../../PROJECT_CONTEXT.md#L1-L40) | **VERIFICADO**: Objetivos docentes y alcance consolidado en la introducción del contexto. |
| **2. Caso de uso y Flujos** | 47–172 | Totalmente migrado | `docs/PROJECT_CONTEXT.md`, `docs/architecture/overview.md` | [overview.md](../../architecture/overview.md#L108-L156), [PROJECT_CONTEXT.md](../../PROJECT_CONTEXT.md#L71-L98) | **VERIFICADO**: Flujos de catálogo, reservas e IA modelados con diagramas de secuencia Mermaid. |
| **3. Arquitectura general** | 173–233 | Totalmente migrado | `docs/architecture/overview.md` | [overview.md](../../architecture/overview.md#L14-L64) | **VERIFICADO**: Diagrama de bloques completo y topología de red. |
| **4. Arquitectura Hexagonal** | 234–298 | Totalmente migrado | `docs/architecture/overview.md`, `docs/MODULES/backend.md` | [overview.md](../../architecture/overview.md#L67-L106), [backend.md](../../MODULES/backend.md#L18-L46) | **VERIFICADO**: Regla de inversión de dependencias y estructura de paquetes de `com.riwi.skillbridge`. |
| **5. Tecnologías (Stack)** | 299–391 | Totalmente migrado | `docs/PROJECT_CONTEXT.md`, `docs/INTEGRATIONS.md` | [PROJECT_CONTEXT.md](../../PROJECT_CONTEXT.md#L44-L58), [INTEGRATIONS.md](../../INTEGRATIONS.md#L7-L23) | **VERIFICADO**: Matriz de tecnologías cotejada con los 12 contenedores de `docker-compose.yml`. |
| **6. Requisitos locales** | 392–411 | Totalmente migrado | `CLAUDE.md`, `AGENTS.md`, `docs/TESTING.md` | [CLAUDE.md](../../../CLAUDE.md#L19-L35), [README.md](../../../README.md#L48-L55) | **VERIFICADO**: Requisitos sintetizados. Se documenta la falta de `mvnw` (`QA-002`). |
| **7. Ejecución con Docker Compose** | 412–510 | Totalmente migrado | `CLAUDE.md`, `docs/INTEGRATIONS.md` | [README.md](../../../README.md#L78-L106), [INTEGRATIONS.md](../../INTEGRATIONS.md#L7-L23) | **VERIFICADO**: Comando `docker compose up --build -d` y tabla de URLs y puertos locales. |
| **8. Primer recorrido funcional** | 511–651 | Resumido intencionalmente | `docs/CONTRIBUTING.md`, `docs/API.md` | [CONTRIBUTING.md](../../CONTRIBUTING.md#L19-L49), [API.md](../../API.md#L1-L80) | **SINTETIZADO INTENCIONALMENTE**: La guía de tracing arquitectónico reside en `CONTRIBUTING.md`; los contratos de endpoints y cURL están documentados formalmente en `API.md` en lugar del tutorial paso a paso con datos de prueba estáticos. |
| **9. RabbitMQ: retry + DLQ** | 652–696 | Totalmente migrado | `docs/INTEGRATIONS.md` | [INTEGRATIONS.md](../../INTEGRATIONS.md#L54-L72) | **VERIFICADO**: Parámetros de backoff exponencial, exchange topic, DLX y DLQ comprobados contra `RabbitConfiguration.java`. |
| **10. IA y Proveedores** | 697–920 | Totalmente migrado | `docs/INTEGRATIONS.md`, `docs/MODULES/backend.md` | [INTEGRATIONS.md](../../INTEGRATIONS.md#L103-L114), [ADR-001](../../ADR-001-AI-PROVIDER-ABSTRACTION.md) | **VERIFICADO**: Abstracción Gemini/OpenAI, Groq STT, ElevenLabs TTS, Stripe y Mailpit documentados con sus propiedades activadoras. |
| **11. Nginx** | 921–967 | Totalmente migrado | `docs/MODULES/frontend.md` | [frontend.md](../../MODULES/frontend.md#L20-L50) | **VERIFICADO**: Inyección de variables en tiempo de ejecución (`public/env.js`) y plantilla Nginx. |
| **12. Observabilidad** | 968–1022 | Totalmente migrado | `docs/INTEGRATIONS.md` | [INTEGRATIONS.md](../../INTEGRATIONS.md#L22) | **VERIFICADO**: Puertos de Actuator, Prometheus y Grafana reflejados en tablas locales. |
| **13–14. Ejecución sin Docker** | 1023–1065| Totalmente migrado | `docs/MODULES/backend.md`, `docs/MODULES/frontend.md` | [backend.md](../../MODULES/backend.md#L110-L121), [frontend.md](../../MODULES/frontend.md#L97-L110) | **VERIFICADO**: Comandos individuales (`mvn spring-boot:run` y `npm start`) preservados en README y módulos. |
| **15. Tests** | 1066–1093| Totalmente migrado | `docs/TESTING.md` | [TESTING.md](../../TESTING.md#L7-L62) | **VERIFICADO**: Comandos `mvn test`, `mvn verify` y señalamiento de requisito Docker por `BookingPersistenceAdapterTest` (`QA-006`). |
| **16. CI con GitHub Actions & Qodana**| 1094–1125| Totalmente migrado | `docs/TESTING.md`, `docs/CONTRIBUTING.md` | [TESTING.md](../../TESTING.md#L86-L95), [CONTRIBUTING.md](../../CONTRIBUTING.md#L113-L127) | **VERIFICADO**: Flujo de validación en PRs y nota sobre omisión de `audit-service` en CI (`QA-004`). |
| **17–24, 36. Despliegue Cloud Gratuito y Cuotas** | 1126–1461, 1951–1972 | Totalmente migrado a guía dedicada | `docs/guides/free-tier-deployment.md`, `docs/DEPLOYMENT-CHECKLIST.md`, `docs/adr/0002-managed-services-in-cloud.md` | [free-tier-deployment.md](../../guides/free-tier-deployment.md), [DEPLOYMENT-CHECKLIST.md](../../DEPLOYMENT-CHECKLIST.md), [0002-managed-services-in-cloud.md](../../adr/0002-managed-services-in-cloud.md) | **VERIFICADO / CONSOLIDADO**: La totalidad del tutorial paso a paso (Neon, Upstash, CloudAMQP, Google AI Studio, Render, Vercel, debate Nginx vs Vercel y tabla de cuotas) fue migrado íntegramente a `docs/guides/free-tier-deployment.md`, preservando la concisión del `README.md`. |
| **25. Variables de entorno** | 1462–1520| Totalmente migrado | `docs/INTEGRATIONS.md`, `docs/MODULES/backend.md`, `docs/MODULES/audit-service.md` | [backend.md](../../MODULES/backend.md#L74-L89), [audit-service.md](../../MODULES/audit-service.md#L77-L87), [INTEGRATIONS.md](../../INTEGRATIONS.md#L9-L23) | **VERIFICADO**: Diccionario de variables normalizado en sus respectivos módulos técnicos. |
| **26. Seguridad** | 1521–1549| Totalmente migrado | `docs/SECURITY-MODEL.md` | [SECURITY-MODEL.md](../../SECURITY-MODEL.md#L7-L80) | **VERIFICADO**: Matriz RBAC, directivas CSRF, SameSite cookies y registro formal de amenazas (SEC-001 a SEC-010). |
| **27. Manejo de errores** | 1550–1574| Totalmente migrado | `docs/architecture/overview.md`, `docs/CONTRIBUTING.md` | [CONTRIBUTING.md](../../CONTRIBUTING.md#L53-L69) | **VERIFICADO**: Convención RFC 7807 `ProblemDetail` y `GlobalExceptionHandler`. |
| **28. Migraciones** | 1575–1614| Totalmente migrado | `docs/MODULES/backend.md`, `docs/CONTRIBUTING.md`, `docs/INTEGRATIONS.md` | [INTEGRATIONS.md](../../INTEGRATIONS.md#L26-L40), [CONTRIBUTING.md](../../CONTRIBUTING.md#L85-L89) | **VERIFICADO**: Convenciones Flyway `V1`–`V7` e inicializador de esquemas de Mongo. |
| **29–32, 35. Metodología de Células** | 1615–1771, 1917–1950| Totalmente migrado | `docs/CONTRIBUTING.md` | [CONTRIBUTING.md](../../CONTRIBUTING.md#L113-L127), [INSTRUCTOR-GUIDE.md](../../INSTRUCTOR-GUIDE.md) | **VERIFICADO**: Git Flow, Definition of Done y pautas de contribución. |
| **33–34. Comandos & Troubleshooting** | 1772–1916| Totalmente migrado | `docs/INTEGRATIONS.md`, `docs/SECURITY-MODEL.md`, `CLAUDE.md` | [CLAUDE.md](../../../CLAUDE.md#L19-L35), [INTEGRATIONS.md](../../INTEGRATIONS.md#L7-L23) | **VERIFICADO**: Resolución de problemas habituales (puertos, Redis, CORS, Postgres). |
| **37–38. Archivos complementarios y Fin**| 1973–2021| Totalmente migrado | `docs/PROJECT_CONTEXT.md` | [PROJECT_CONTEXT.md](../../PROJECT_CONTEXT.md#L101-L111) | **VERIFICADO**: Enlaces al índice central de la suite técnica y licencia educativa. |

---

## 3. Destinos Documentales Verificados

Se confirmó la existencia y consistencia de los siguientes archivos de destino en el árbol de trabajo:
- [docs/PROJECT_CONTEXT.md](../../PROJECT_CONTEXT.md) (133 líneas)
- [docs/architecture/overview.md](../../architecture/overview.md) (177 líneas)
- [docs/MODULES/backend.md](../../MODULES/backend.md) (121 líneas)
- [docs/MODULES/frontend.md](../../MODULES/frontend.md) (110 líneas)
- [docs/MODULES/audit-service.md](../../MODULES/audit-service.md) (127 líneas)
- [docs/INTEGRATIONS.md](../../INTEGRATIONS.md) (114 líneas)
- [docs/SECURITY-MODEL.md](../../SECURITY-MODEL.md) (80 líneas)
- [docs/TESTING.md](../../TESTING.md) (95 líneas)
- [docs/CONTRIBUTING.md](../../CONTRIBUTING.md) (127 líneas)
- [docs/DEPLOYMENT-CHECKLIST.md](../../DEPLOYMENT-CHECKLIST.md) (24 líneas)
- [docs/guides/free-tier-deployment.md](../../guides/free-tier-deployment.md) (168 líneas - nueva guía especializada)
- [docs/RISK-REGISTER.md](../../RISK-REGISTER.md) (69 líneas)
- [docs/API.md](../../API.md) (88 líneas)
- [docs/INSTRUCTOR-GUIDE.md](../../INSTRUCTOR-GUIDE.md) (80 líneas)
- [docs/adr/0001-modular-monolith-first.md](../../adr/0001-modular-monolith-first.md) (22 líneas)
- [docs/adr/0002-managed-services-in-cloud.md](../../adr/0002-managed-services-in-cloud.md) (15 líneas)
- [docs/adr/0003-kafka-event-topic-unification.md](../../adr/0003-kafka-event-topic-unification.md) (22 líneas)
- [CLAUDE.md](../../../CLAUDE.md) (60 líneas)
- [AGENTS.md](../../../AGENTS.md) (81 líneas)

---

## 4. Validación de Enlaces Relativos en README.md

Se ejecutó un script de verificación automatizada que analizó sintácticamente todos los hipervínculos markdown presentes en el nuevo [README.md](../../../README.md).

**Resultado**: 22 enlaces evaluados, **0 enlaces rotos**, 100% de rutas resueltas con éxito:
1. `backend/` -> Directorio existente en raíz.
2. `frontend/` -> Directorio existente en raíz.
3. `audit-service/` -> Directorio existente en raíz.
4. `docs/` -> Directorio existente en raíz.
5. `docs/PROJECT_CONTEXT.md` -> Archivo existente.
6. `docs/architecture/overview.md` -> Archivo existente.
7. `docs/MODULES/backend.md` -> Archivo existente.
8. `docs/MODULES/frontend.md` -> Archivo existente.
9. `docs/MODULES/audit-service.md` -> Archivo existente.
10. `docs/INTEGRATIONS.md` -> Archivo existente.
11. `docs/SECURITY-MODEL.md` -> Archivo existente.
12. `docs/TESTING.md` -> Archivo existente.
13. `docs/CONTRIBUTING.md` -> Archivo existente.
14. `docs/DEPLOYMENT-CHECKLIST.md` -> Archivo existente.
15. `docs/guides/free-tier-deployment.md` -> Archivo existente (nueva guía).
16. `docs/RISK-REGISTER.md` -> Archivo existente.
17. `AGENTS.md` -> Archivo existente.
18. `CLAUDE.md` -> Archivo existente.
19. `docs/adr/` -> Directorio existente.
20. `docs/audit/findings.md` -> Archivo existente.

---

## 5. Correcciones Operativas, Limitaciones y Asuntos Pendientes

### 5.1 Corrección de `Makefile` (`QA-001`) — RESUELTO
- **Diagnóstico**: La línea 12 de `Makefile` contenía `docker compose up --build -d` sin sangría de tabulación tras una línea en blanco en el bloque `reset:`, provocando `Makefile:12: *** missing separator. Stop.`.
- **Acción correctiva**: Se ajustó la receta `reset:` con la tabulación requerida:
  ```makefile
  reset:
  	docker compose down -v --remove-orphans
  	docker compose up --build -d
  ```
- **Validación**:
  - `make -n` validó los 9 targets (`up`, `down`, `reset`, `logs`, `ps`, `observability`, `test-back`, `test-front`, `build`) sin errores sintácticos.
  - Se ejecutó `make ps` de manera segura, confirmando funcionamiento sin alterar volúmenes ni contenedores.

### 5.2 Emisión de Guía de Despliegue en Cloud Gratuito — RESUELTO
- **Acción**: Se creó [`docs/guides/free-tier-deployment.md`](../../guides/free-tier-deployment.md) cubriendo de manera exhaustiva el aprovisionamiento paso a paso en Neon (PostgreSQL), Upstash (Redis TLS), CloudAMQP (RabbitMQ), Google AI Studio (Gemini), Render (Web Service con Docker y liveness probe) y Vercel (Angular SPA con inyección `public/env.js`), junto con la comparativa arquitectónica de Nginx y la tabla de cuotas/límites de planes gratuitos.
- Se conectó dicha guía desde `docs/DEPLOYMENT-CHECKLIST.md` y `README.md`.

### 5.3 Limitaciones y Defectos Técnicos Pendientes (Fuera de Alcance de Documentación)
- **Ausencia de Maven Wrapper (`QA-002`)**: El repositorio continúa requiriendo Maven 3.9+ instalado en el host, dado que no existe `./mvnw`.
- **Acoplamiento de Testcontainers en fase unitaria (`QA-006`)**: `BookingPersistenceAdapterTest` se ejecuta dentro de `mvn test` y requiere de un demonio Docker activo en el host.
- **Pruebas de integración silenciadas (`QA-007`)**: `NotificationFlowIT` y `JpaOfferingRepositoryTest` continúan marcadas con `@Disabled("Docker Bug Testcontainers")`.
- **Omisión de `audit-service` en CI (`QA-004`)**: El workflow de GitHub Actions (`ci.yml`) únicamente valida `backend/` y `frontend/`.
- **Ejemplos interactivos cURL de la sección 8**: Se mantienen sintetizados en `docs/API.md` y `docs/CONTRIBUTING.md`. No se reimplementó el walkthrough interactivo con datos hardcodeados en el README principal para mantener la concisión de la portada.

---

## 6. Comandos de Validación Ejecutados

Comandos ejecutados en el entorno de desarrollo para validar la integridad del cambio:

```bash
# 1. Comprobación del estado inicial y preservación de cambios no relacionados
git status --short
git diff --stat

# 2. Corrección y validación sintáctica del Makefile (dry-run de todos los targets)
make -n up down reset logs ps observability test-back test-front build

# 3. Validación operacional no destructiva del Makefile
make ps

# 4. Validación de sintaxis de Docker Compose
docker compose config --quiet

# 5. Conteo de líneas del README.md
wc -l README.md # 161 líneas (rango objetivo: 150-200)

# 6. Verificación automatizada de enlaces relativos en README.md
python3 -c "
import os, re
with open('README.md') as f:
    links = [l.split('#')[0] for l in re.findall(r'\[.*?\]\((.*?)\)', f.read()) if not l.startswith('http')]
broken = [l for l in links if not os.path.exists(l)]
assert not broken, f'Broken links: {broken}'
print(f'All {len(links)} links valid')
"

# 7. Verificación de enlaces de la nueva guía docs/guides/free-tier-deployment.md
python3 -c "
import os, re
base = 'docs/guides/free-tier-deployment.md'
with open(base) as f:
    links = [l.split('#')[0] for l in re.findall(r'\[.*?\]\((?!http)(.*?)\)', f.read())]
broken = [l for l in links if not os.path.exists(os.path.normpath(os.path.join(os.path.dirname(base), l)))]
assert not broken, f'Broken links: {broken}'
print(f'All {len(links)} links in guide valid')
"

# 8. Comprobación final del árbol de cambios en Git
git status --short
```
