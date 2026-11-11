# SDD-WORKFLOW — Puente entre Spec-Driven Development y el Orquestador Worktree de agy

Este documento describe la integración operacional entre el flujo **Spec-Driven Development (GitHub Spec Kit)** y el orquestador de agentes en Git worktrees aislados (`scripts/agent-worktree-agy.sh`), documentado en [WORKTREE-ORCHESTRATION.md](WORKTREE-ORCHESTRATION.md). Es el flujo obligatorio para trabajo delegado; para clasificar el cambio antes de llegar aquí, consulta [SDD-HYBRID-GUIDE.md](SDD-HYBRID-GUIDE.md).

---

## 1. Filosofía y División de Responsabilidades (Humanos vs. Agentes)

El flujo SDD completo garantiza que ningún worker delegado escriba código sin una especificación clara, un diseño técnico validado y límites estrictos de alcance. Los cambios pequeños no se delegan mediante este puente y siguen la evidencia proporcional definida en la guía híbrida.

```
[User Story / Issue]
        │
        ▼
1. /speckit.specify ────► specs/<NNN>-<slug>/spec.md
        │                                 │
        ▼                                 ▼
   [Humano] ─────────────────────► [APROBACIÓN HUMANA: Requisitos y Criterios]
        │
        ▼
2. /speckit.plan    ────► specs/<NNN>-<slug>/plan.md
        │                                 │
        ▼                                 ▼
   [Humano] ─────────────────────► [APROBACIÓN HUMANA: Arquitectura y Constitución]
        │
        ▼
3. /speckit.tasks   ────► specs/<NNN>-<slug>/tasks.md
        │
        ▼
4. Delegación SDD ──► scripts/agent-worktree-agy.sh
   (Por Tarea/Fase)          │
                             ▼
                    [Worktree Aislado]
                    (Agente agy: T001, T002...)
                             │
                             ▼
                    [Revisión Humana / Claude Orchestrator]
                    (Patch, Reporte, Violaciones)
```

| Fase SDD | Artefacto | Responsable de Aprobación | Responsable de Ejecución |
|---|---|---|---|
| **1. Especificación** | `specs/<NNN>-<slug>/spec.md` | **Humano** (Revisa necesidades de negocio, prioridades P1/P2/P3 y escenarios Given-When-Then) | Humano + Agente (`/speckit.specify`, `/speckit.clarify`) |
| **2. Plan Técnico** | `specs/<NNN>-<slug>/plan.md` | **Humano** (Revisa conformidad con la constitución, puertos hexagonales y modelos de datos) | Agente (`/speckit.plan`) |
| **3. Desglose de Tareas** | `specs/<NNN>-<slug>/tasks.md` | **Humano** (Verifica orden de dependencia, tareas MVP y paralelización `[P]`) | Agente (`/speckit.tasks`, `/speckit.analyze`) |
| **4. Implementación** | Código en `backend/`, `frontend/`, etc. | **Humano / Orquestador** (Valida parche, reporte y re-ejecuta pruebas) | **Agentes Worker** en worktrees (`agent-worktree-agy.sh`) |

---

## 2. De `tasks.md` a la Spec de `agent-worktree-agy.sh`

Cada tarea individual (p. ej., `T014`) o fase acotada de `tasks.md` se convierte en un archivo de especificación para el worker.

### Reglas para la Spec del Worker
1. **Encabezado obligatorio de Criterios de Aceptación**: Debe contener exactamente un encabezado de nivel 2 con `"Acceptance criteria"` o `"Criterios de aceptación"`. De lo contrario, `agent-worktree-agy.sh` rechaza la ejecución (código de salida 2).
2. **Límite de tamaño**: El archivo spec debe ser $\le 48\text{ KiB}$.
3. **Contenido concreto**:
   - Objetivo de la tarea.
   - Lista explícita de comportamientos esperados y casos de error.
   - Mandato de pruebas unitarias relevantes.
   - Prohibición de modificaciones fuera del scope.

### Reglas para Parámetros del Orquestador
1. **`--task-id`**: Identificador alfanumérico seguro (`[A-Za-z0-9][A-Za-z0-9_-]{0,39}`), e.g. `HU27-T014`.
2. **`--base`**: Rama o commit donde ya se encuentran confirmados los archivos de contexto y la estructura inicial (p. ej., `feature/027-booking-rescheduling`). **El worktree solo ve archivos committeados en la base.** Si `plan.md` o `spec.md` no están committeados en `--base`, el script se detiene (código de salida 3).
3. **`--scope`**: Rutas relativas al repositorio de los archivos que el agente tiene autorización de crear o modificar. Deben existir en la base (o debe existir su directorio padre).
4. **`--context`**: Rutas de los documentos de referencia indispensables para la tarea. Típicamente:
   `--context specs/<NNN>-<slug>/spec.md --context specs/<NNN>-<slug>/plan.md`.

---

## 3. Procedimiento de Revisión e Integración

Una vez que el worker en el worktree finaliza:

1. **Inspección de artefactos en el directorio del run**:
   ```bash
   RUN="../SkillBridge-AI-JAVA-Angular-agent-worktrees/<run-id>"
   cat "$RUN/summary.md"
   cat "$RUN/violations.txt"     # Debe estar vacío si no hubo violaciones de guardarraíles
   cat "$RUN/report.md"           # Reporte de evidencia del worker
   ```
2. **Revisión del diff**:
   ```bash
   less "$RUN/changes.patch"
   git -C "$RUN/worktree" status --short
   git -C "$RUN/worktree" diff
   ```
3. **Verificación independiente de pruebas**:
   Re-ejecutar las pruebas reportadas en el worktree antes de integrar:
   ```bash
   (cd "$RUN/worktree/backend" && mvn test -Dtest=TargetTest)
   ```
4. **Aplicación al espacio de trabajo principal**:
   ```bash
   git apply --check "$RUN/changes.patch"   # Comprobación en seco
   git apply "$RUN/changes.patch"           # Aplicación definitiva
   ```
5. **Cierre y limpieza del worktree**:
   ```bash
   scripts/agent-worktree-agy.sh mark-reviewed --run-id "<run-id>"
   scripts/agent-worktree-agy.sh cleanup       --run-id "<run-id>"
   ```

---

## 4. Ejemplo Concreto: HU-27 (Reprogramación de Reservas)

### Contexto
- **User Story**: HU-27 — "Como cliente, deseo reprogramar una reserva confirmada seleccionando una nueva fecha/hora disponible, para ajustar mis planes sin perder el pago si cumplo la política de preaviso".
- **Directorio SDD**: `specs/027-booking-rescheduling/`
- **Rama**: `feature/027-booking-rescheduling`
- **Tarea en `tasks.md`**:
  ```markdown
  - [ ] T014 [US1] Implementar RescheduleBookingUseCase en el servicio de aplicación BookingService
  ```

### Paso 1: Crear el archivo de spec para el worker (`/tmp/hu27-t014-spec.md`)
```markdown
# Tarea HU27-T014 — Implementar caso de uso de reprogramación de reservas

Implementar la lógica de negocio del caso de uso de reprogramación en el servicio de aplicación `BookingService`, conforme a la especificación funcional y el plan técnico.

## Criterios de aceptación
- La reserva solo puede reprogramarse si se encuentra en estado `CONFIRMED`.
- Se valida disponibilidad del proveedor en el nuevo horario a través del puerto de persistencia.
- Si el preaviso es mayor o igual a 24 horas, la tarifa de penalidad es 0.
- Si el preaviso es menor a 24 horas, se calcula una penalidad del 15% sobre el valor total.
- Se publica el evento de dominio `BookingRescheduledEvent` a través del puerto de mensajería unificado.
- Pasan todas las pruebas unitarias en `BookingServiceTest` ejecutadas con `mvn test`.
- No se modifica ningún archivo fuera del scope autorizado.
```

### Paso 2: Invocar `agent-worktree-agy.sh`
```bash
scripts/agent-worktree-agy.sh run \
  --task-id HU27-T014 \
  --base feature/027-booking-rescheduling \
  --spec /tmp/hu27-t014-spec.md \
  --scope backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java \
  --scope backend/src/main/java/com/riwi/skillbridge/application/port/in/RescheduleBookingUseCase.java \
  --scope backend/src/test/java/com/riwi/skillbridge/application/service/BookingServiceTest.java \
  --context specs/027-booking-rescheduling/spec.md \
  --context specs/027-booking-rescheduling/plan.md \
  --non-interactive
```

### Paso 3: Revisión y aplicación
```bash
# 1. Verificar estado y ausencia de violaciones
cat ../SkillBridge-AI-JAVA-Angular-agent-worktrees/HU27-T014-*/violations.txt
cat ../SkillBridge-AI-JAVA-Angular-agent-worktrees/HU27-T014-*/report.md

# 2. Aplicar parche a la rama de feature
git apply --check ../SkillBridge-AI-JAVA-Angular-agent-worktrees/HU27-T014-*/changes.patch
git apply ../SkillBridge-AI-JAVA-Angular-agent-worktrees/HU27-T014-*/changes.patch

# 3. Comprobar compilación local
mvn -o validate -f backend/pom.xml

# 4. Registrar revisión y limpiar el worktree
RUN_ID=$(cat ../SkillBridge-AI-JAVA-Angular-agent-worktrees/HU27-T014-*/meta.env | grep RUN_ID | cut -d= -f2)
scripts/agent-worktree-agy.sh mark-reviewed --run-id "$RUN_ID"
scripts/agent-worktree-agy.sh cleanup --run-id "$RUN_ID"
```
