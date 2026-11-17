# SDD híbrido proporcional

Esta guía aplica [ADR-0005](../adr/0005-proportionate-hybrid-sdd.md). Su objetivo es preservar trazabilidad donde reduce riesgo y evitar artefactos que repiten lo que ya prueban el código, la configuración o las pruebas.

## 1. Clasificar antes de implementar

Clasificar por impacto y riesgo, no por líneas modificadas.

| Nivel | Señales | Evidencia mínima obligatoria | No exigir por defecto |
|---|---|---|---|
| **Pequeño** | Corrección localizada, refactor interno, ajuste visual o configuración sin nuevo contrato ni cambio de seguridad/datos/eventos | Issue o descripción de PR con objetivo, criterios verificables y prueba ejecutada | `specs/`, plan, tareas, `analyze` |
| **Mediano** | Nueva regla de negocio o comportamiento visible aislado; endpoint/UI limitado; varios archivos con una decisión técnica acotada | `spec.md` breve con alcance, criterios de aceptación, bordes y prueba | Plan, tareas y artefactos auxiliares, salvo disparadores siguientes |
| **Grande / alto riesgo** | Varios módulos; migración con semántica de negocio; seguridad, pagos, IA externa, evento Kafka/RabbitMQ, API pública, puerto/adaptador, o trabajo paralelo | `spec.md`, `plan.md`, `tasks.md`, revisión humana y pruebas | Ninguno del ciclo central |

Un cambio mediano pasa a ciclo completo si necesita una decisión arquitectónica relevante, más de una fase dependiente, coordinación entre agentes, o ejecución mediante `scripts/agent-worktree-agy.sh`.

Ante duda entre dos niveles, elegir el superior y dejar la razón en el issue o PR.

## 2. Qué se mantiene vivo

- **Constitución**: solo invariantes técnicos y de seguridad. Cambiarla requiere el proceso de enmienda y ADR.
- **Documentación canónica** (`docs/MODULES/`, arquitectura, seguridad e integraciones): actualizarla cuando cambie el contrato que describe.
- **Especificación de comportamiento**: mantenerla durante la entrega de cambios medianos/grandes. Si una nueva intención modifica una entrega cerrada, crear una especificación sucesora y enlazar la anterior.

Los planes, tareas, investigaciones, modelos de datos y checklists de una entrega completada son históricos. No se reescriben para aparentar que siempre coincidieron con el código; el cambio posterior obtiene su propio registro.

## 3. Artefactos y comandos

| Situación | Flujo |
|---|---|
| Pequeña | Definir aceptación en issue/PR → implementar → ejecutar comprobación pertinente → registrar evidencia |
| Mediana simple | `/speckit.specify` → revisar la spec → implementar → validar |
| Mediana con diseño/delegación | `/speckit.specify` → `/speckit.plan` → `/speckit.tasks` → `analyze` si aplica → implementar |
| Grande / alto riesgo | `/speckit.specify` → `/speckit.clarify` si hay ambigüedad → `/speckit.plan` → `/speckit.checklist` → `/speckit.tasks` → `/speckit.analyze` → implementar por fases |

Los comandos disponibles en esta copia del repositorio son `constitution`, `specify`, `clarify`, `plan`, `tasks`, `analyze`, `implement` y `checklist`. Las rutas y convenciones de cada integración están en [`.specify/README.md`](../../.specify/README.md).

Generar `research.md`, `data-model.md`, `quickstart.md` o `contracts/` solo si existe una decisión, modelo o contrato que sea necesario revisar fuera del código. No copiar en Markdown clases, DTOs, migraciones ni configuración que ya sean la fuente de verdad.

## 4. Puertas de calidad

### `analyze`

Ejecutar `analyze` solo después de que existan `spec.md`, `plan.md` y `tasks.md`, antes de implementar, cuando haya cualquiera de estas señales:

- varios módulos, historias de usuario o fases;
- seguridad, pagos, datos, eventos, IA o API pública;
- tareas delegadas a worktrees/agentes;
- cambio de plan/tareas tras una aprobación;
- requisito no funcional comprobable de rendimiento, disponibilidad, privacidad o auditoría.

Es un análisis de solo lectura: detecta incoherencias entre artefactos, pero no sustituye pruebas ni revisión de código.

### `converge`

`converge` no está instalado en los comandos locales actuales, así que no se invoca ni se simula. Tras una actualización revisada que lo añada, usarlo únicamente después de implementar una feature que ya tenga spec, plan y tareas completos. Su uso previsto es detectar brechas y añadir tareas pendientes; no sincroniza automáticamente especificaciones ni planes.

## 5. Ejemplo: cambio mediano que escala a flujo completo

**Petición:** “Como cliente, quiero filtrar ofertas por rango de precio y conservar el filtro al volver al catálogo.”

1. Se clasifica inicialmente como **mediano**: cambia comportamiento visible y requiere criterios de aceptación.
2. Se crea `specs/029-offering-price-filter/spec.md` con: rango permitido, comportamiento sin resultados, conservación al navegar y pruebas de aceptación. Se evita documentar componentes Angular o consultas JPA concretas.
3. Al descubrir que el filtro modifica el contrato `GET /api/offerings`, el estado de URL del frontend y la caché de catálogo, pasa a **mediano con diseño**. Se genera `plan.md` con la decisión de contrato, límites de caché y Constitution Check.
4. Como backend y frontend pueden delegarse en paralelo, se genera `tasks.md`. Cada tarea declara archivo/scope y prueba pertinente.
5. Se ejecuta `analyze` antes de delegar para comprobar que los criterios de rango, caché, URL y autorización tengan tarea asociada.
6. Los workers reciben solo la spec, el plan y la tarea/fase autorizada. Tras integrar, se ejecutan las pruebas backend/frontend que correspondan y se actualiza la documentación de API o módulo solo si su contrato público cambió.
7. Al cerrar, `plan.md` y `tasks.md` quedan como historial. Una futura ampliación, por ejemplo filtros por disponibilidad, abre una spec enlazada en vez de reescribir la entrega 029.

## 6. Medir antes de endurecer el proceso

Durante al menos seis cambios medianos o grandes, registrar en el issue/PR: clasificación, minutos de análisis/documentación/implementación, tokens reportados por la herramienta cuando existan, tiempo hasta revisión, defectos hallados, y retrabajo. Revisar los datos antes de ampliar los requisitos documentales.
