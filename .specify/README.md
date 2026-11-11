# .specify/ — GitHub Spec Kit Configuration & Assets

Estructura oficial de configuración y artefactos de **GitHub Spec Kit** adaptada para **SkillBridge AI**.

> **Política vigente**: SkillBridge usa SDD híbrido proporcional. Antes de crear artefactos, clasifica el cambio con la [guía operativa](../docs/agents/SDD-HYBRID-GUIDE.md). El ciclo completo se reserva para cambios grandes, de alto riesgo o delegados a worktrees; no es obligatorio para correcciones pequeñas.

## Estructura del directorio

```text
.specify/
├── memory/
│   └── constitution.md        # Constitución técnica, arquitectónica y de gobernanza de SkillBridge AI (v1.1.0)
├── templates/
│   ├── spec-template.md       # Plantilla de especificación funcional (user stories, requisitos, criterios de aceptación)
│   ├── plan-template.md       # Plantilla de plan técnico (arquitectura, dependencias, modelo de datos, contratos)
│   ├── tasks-template.md      # Plantilla de desglose de tareas ordenadas por dependencia y fase
│   ├── checklist-template.md  # Plantilla de listas de chequeo y puertas de calidad
│   ├── agent-file-template.md # Plantilla para generación y actualización de contexto de agentes
│   └── constitution-template.md # Plantilla base de constitución
├── scripts/
│   └── bash/
│       ├── check-prerequisites.sh # Verificación de prerequisitos y extracción de metadatos de feature
│       ├── common.sh              # Biblioteca compartida de utilidades para scripts bash de Spec Kit
│       ├── create-new-feature.sh  # Creación automatizada de ramas de feature y carpetas specs/<NNN>-<slug>/
│       ├── resolve-template.sh    # Resolución jerárquica de plantillas a través de la pila de overrides
│       ├── setup-plan.sh          # Inicialización del plan de implementación para la feature activa
│       ├── setup-tasks.sh         # Inicialización del archivo de tareas para la feature activa
│       └── update-agent-context.sh # Actualización de archivos de contexto de agentes (CLAUDE.md, AGENTS.md)
└── README.md                  # Este documento
```

## Flujo Spec-Driven Development (SDD)

Para una feature que requiere ciclo completo, los comandos disponibles son:

1. **Constitution** (`speckit.constitution`): Establece los invariantes arquitectónicos, reglas de seguridad y estándares de verificación en `.specify/memory/constitution.md`.
2. **Specify** (`speckit.specify`): Genera la especificación funcional en `specs/<NNN>-<slug>/spec.md` a partir de la User Story.
3. **Clarify** (`speckit.clarify`): Resuelve ambigüedades e identifica casos borde antes de la planificación.
4. **Plan** (`speckit.plan`): Desarrolla el diseño técnico en `specs/<NNN>-<slug>/plan.md` verificando la constitución.
5. **Tasks** (`speckit.tasks`): Desglosa la implementación en tareas independientes y testeables en `specs/<NNN>-<slug>/tasks.md`.
6. **Analyze** (`speckit.analyze`): Verifica la consistencia cruzada entre spec, plan y tasks.
7. **Implement** (`speckit.implement`): Ejecuta las tareas en fases delegando a worktrees aislados.
8. **Checklist** (`speckit.checklist`): Valida puertas de calidad y requisitos no funcionales.

`converge` no forma parte todavía de los comandos vendorizados en este repositorio. No debe añadirse ni invocarse sin una actualización revisada de Spec Kit.

## Selección rápida de flujo

| Cambio | Punto de partida |
|---|---|
| Corrección localizada sin nuevo comportamiento | Issue/PR con aceptación y evidencia; no crear artefactos SDD |
| Comportamiento aislado | `speckit.specify` y revisión de la spec |
| Diseño, delegación o varias fases | Añadir `speckit.plan` y `speckit.tasks` |
| Seguridad, pagos, datos, eventos, IA, API pública o varios módulos | Ciclo completo y `speckit.analyze` antes de implementar |

Consulta el ejemplo completo de clasificación, escalamiento y cierre en [SDD-HYBRID-GUIDE.md](../docs/agents/SDD-HYBRID-GUIDE.md).
