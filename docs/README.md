# Documentación de SkillBridge AI

Índice de `docs/`. Para empezar a trabajar con un agente de código, lee primero [`CLAUDE.md`](../CLAUDE.md) y [`AGENTS.md`](../AGENTS.md): contienen la tabla que indica qué documento leer según la tarea.

## Guías vivas (en la raíz de `docs/`)

| Documento | Para qué sirve |
|---|---|
| [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | Contexto del producto y flujos de dominio |
| [API.md](API.md) | Contrato de la API REST |
| [INTEGRATIONS.md](INTEGRATIONS.md) | Kafka, RabbitMQ, IA, pagos y demás integraciones |
| [SECURITY-MODEL.md](SECURITY-MODEL.md) | Autenticación, RBAC y CSRF |
| [TESTING.md](TESTING.md) | Tipos de pruebas y cómo ejecutarlas |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Flujo de trabajo para añadir funcionalidades |
| [CI-CD.md](CI-CD.md) | Pipeline y protección de ramas |
| [DEPLOYMENT-CHECKLIST.md](DEPLOYMENT-CHECKLIST.md) | Verificación previa al despliegue |
| [RISK-REGISTER.md](RISK-REGISTER.md) | Riesgos conocidos y deuda técnica |

## Carpetas

| Carpeta | Contenido |
|---|---|
| [`architecture/`](architecture/) | [Visión general](architecture/overview.md), [Kafka](architecture/kafka.md), [resumen inicial](architecture/ARCHITECTURE.md) y [diagrama](architecture/architecture.mmd) |
| [`adr/`](adr/README.md) | Decisiones de arquitectura (0001 a 0007) con [índice](adr/README.md); incluye [ADR-001 (proveedores de IA)](adr/ADR-001-AI-PROVIDER-ABSTRACTION.md) y su [comparativa](adr/AI_PROVIDER_COMPARISON.md) |
| [`MODULES/`](MODULES/) | Guías por módulo: [backend](MODULES/backend.md), [frontend](MODULES/frontend.md), [audit-service](MODULES/audit-service.md) |
| [`contracts/`](contracts/) | Contratos entre componentes: [mentorías](contracts/mentorships/gap-matrix.md) y [eventos de analítica](contracts/analytics-events.md) (HU-22 y HU-25) |
| [`guides/`](guides/) | Guías paso a paso, p. ej. [despliegue en capa gratuita](guides/free-tier-deployment.md) |
| [`features/`](features/) | Resúmenes de implementación de HUs ya entregadas (HU-09, HU-12, HU-17, HU-23) |
| [`agents/`](agents/) | Operación de agentes, SDD y worktrees |
| [`refinement/`](refinement/) | Refinamiento de HUs y registro de decisiones |
| [`course/`](course/) | Material pedagógico: backlog para las células y guía del instructor |
| [`history/`](history/) | Informes de un momento concreto (octubre 2026): notas de versión, demo, bloqueos y conflictos. Se conservan como historia, no como guía |
| [`audit/`](audit/) | Evidencia de auditoría. **Protegida: no se edita** |

## Fuera de `docs/`

- [`specs/`](../specs/README.md): especificaciones de HUs (Spec-Driven Development).
- [`CHANGELOG.md`](../CHANGELOG.md) y [`README.md`](../README.md).

## Reglas para mantener esto ordenado

- Un documento vivo por tema. Si duplica a otro, se fusiona en el canónico.
- Los enlaces entre documentos son **relativos**; nunca rutas absolutas de una máquina concreta.
- Lo que describe un momento concreto va a `history/` o `features/`; no se reescribe para que parezca actual.
