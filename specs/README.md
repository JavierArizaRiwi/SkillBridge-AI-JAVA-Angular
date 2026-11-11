# specs/ — Especificaciones Spec-Driven Development (GitHub Spec Kit)

Este directorio alberga los artefactos de diseño, análisis y planificación de funcionalidades de **SkillBridge AI**, gestionados conforme al estándar de **GitHub Spec Kit**. Se usa cuando la clasificación de la [guía SDD híbrida](../docs/agents/SDD-HYBRID-GUIDE.md) requiere una especificación; no se crea para cambios pequeños.

Cada funcionalidad o User Story (HU) cuenta con su propio subdirectorio aislado: `specs/<NNN>-<slug>/`.

---

## 1. Convención de Nomenclatura y Enlace a GitHub

| Elemento | Convención | Ejemplo |
|---|---|---|
| **Directorio de Feature** | `specs/<NNN>-<slug>/` | `specs/025-ai-recommendations/`, `specs/027-booking-rescheduling/` |
| **Prefijo Numérico `<NNN>`** | 3 dígitos con relleno de ceros coincidente con la User Story / Issue de GitHub (`HU-X` → `00X-`, `HU-25` → `025-`) | `HU-25` → `025-`, `HU-27` → `027-` |
| **Rama Git de Feature** | `feature/<NNN>-<slug>` | `feature/025-ai-recommendations`, `feature/027-booking-rescheduling` |
| **Enlace a Issue en `spec.md`** | Campo obligatorio en los metadatos de cabecera enlazando el Issue de GitHub | `**Issue**: [HU-25](https://github.com/riwi-io/SkillBridge-AI/issues/25) (#25)` |

---

## 2. Ciclo de Vida de Artefactos por Feature

Dentro de cada directorio `specs/<NNN>-<slug>/` residen los documentos de las distintas fases SDD:

```text
specs/<NNN>-<slug>/
├── spec.md              # Fase 1: Especificación funcional (Qué y Por qué). Aprobada por el Humano.
├── plan.md              # Si hay diseño material, alto riesgo o delegación.
├── tasks.md             # Si hay varias fases, agentes o worktrees.
├── research.md          # Solo si una decisión requiere investigación conservable.
├── data-model.md        # Solo si el modelo necesita revisión fuera del código.
├── contracts/           # Solo si existe un contrato externo que revisar.
└── checklists/          # Puerta de calidad para trabajo con ambigüedad/riesgo.
```

1. **`spec.md`** (Generado con `/speckit.specify` o `/speckit.clarify`):
   - Define el problema, historias de usuario priorizadas (P1: MVP, P2, P3), criterios de aceptación en formato Given-When-Then y casos borde.
   - Incluye el enlace formal al Issue de GitHub correspondiente.
   - **Puerta de Calidad**: Requiere revisión y aprobación explícita de un humano antes de pasar al plan.
2. **`plan.md`** (Generado con `/speckit.plan`, cuando aplique):
   - Traduce la especificación en una arquitectura ejecutable respetando la constitución de SkillBridge AI (`.specify/memory/constitution.md`).
   - Define puertos entrantes/salientes, adaptadores, DTOs, cambios en base de datos y estrategias de observabilidad.
   - **Puerta de Calidad**: Requiere revisión y aprobación explícita de un humano antes de desglosar tareas.
3. **`tasks.md`** (Generado con `/speckit.tasks`, cuando aplique):
   - Desglosa el plan en tareas unitarias, independientes y ordenadas por fase/dependencia con formato `[T001] [P?] [US1] Descripción`.
   - Cada tarea o fase de tareas se convierte en la entrada (`--spec`) para delegación a agentes en worktrees aislados.
4. **Artefactos auxiliares opcionales**:
   - `research.md`, `data-model.md`, `contracts/` y `checklists/` proporcionan contexto profundo bajo demanda para los agentes de implementación. No duplican información ya expresada adecuadamente por código, pruebas o configuración.

Al cerrar una feature, plan, tareas y artefactos auxiliares se conservan como historial. La especificación mantiene el comportamiento acordado durante la entrega; una intención posterior abre una feature enlazada, en vez de reescribir el historial.

---

## 3. Reglas de Desarrollo en Paralelo y Concurrencia

Para garantizar que múltiples ingenieros y agentes puedan trabajar simultáneamente sin conflictos ni pisado de trabajo:

1. **Aislamiento de Directorio**: Cada agente o desarrollador trabaja exclusivamente en el directorio `specs/<NNN>-<slug>/` de su funcionalidad asignada.
2. **Protección de Features Paralelas**:
   - Está terminantemente prohibido modificar, sobreescribir o borrar directorios de otras funcionalidades (`specs/0*`) en ramas o worktrees ajenos.
3. **Flujo de Ramas**:
   - Cada feature se desarrolla en su rama `feature/<NNN>-<slug>` creada a partir de `develop` (o la base confirmada).
   - Los cambios de implementación se delegan a worktrees de corta duración mediante `scripts/agent-worktree-agy.sh`.
   - La integración a la rama principal de la feature se efectúa mediante revisión de diffs y pruebas verificadas.

---

## 4. Dependencias y Secuenciación de Features Pendientes

Para consultar el grafo completo de dependencias, prerrequisitos técnicos y orden de ejecución recomendado entre las especificaciones activas (HU-22, HU-25, HU-26, HU-27, HU-28, HU-29, HU-16), consulta:

- 📊 **[Matriz y Grafo de Dependencias de HUs Pendientes](../docs/refinement/hu-dependencies.md)**
- 📋 **[Refinamiento General de HUs (noviembre 2026)](../docs/refinement/2026-11-hu-refinement.md)**

