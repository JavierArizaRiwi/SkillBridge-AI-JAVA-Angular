# Auditoría automática del repositorio

- Generado: 2026-10-21 16:55 -05
- Raíz analizada: `.` (se omite la ruta absoluta local para que el informe sea portable).
- Ejecución: local; sin enviar el código a servicios externos; sin ejecutar la aplicación.
- Archivos inventariados: **380**.
- Tamaño total inventariado: **1.97 MiB**.
- Archivos de texto candidatos: **370**.
- Líneas de texto leídas: **24,570**; caracteres: **1,094,698**.
- Estimación muy aproximada de tamaño textual: **273,674 tokens** (caracteres / 4; no equivale al tokenizer de un modelo).
- Git: rama `develop`; estado `4 cambio(s) pendiente(s)`.

## Tipos de archivo

| Extensión | Cantidad |
|---|---|
| .java | 229 |
| .ts | 39 |
| .md | 38 |
| .json | 16 |
| [sin extensión] | 12 |
| .sql | 7 |
| .yml | 7 |
| .html | 6 |
| .css | 4 |
| .sh | 4 |
| .js | 3 |
| .xml | 3 |
| .mjs | 2 |
| .ps1 | 2 |
| .txt | 2 |
| .conf | 1 |
| .ico | 1 |
| .mmd | 1 |
| .py | 1 |
| .template | 1 |
| .yaml | 1 |

## Directorios / áreas con más archivos

| Ruta aproximada | Archivos |
|---|---|
| backend/src | 229 |
| audit-service/frontend | 35 |
| frontend/src | 30 |
| docs | 20 |
| frontend | 12 |
| audit-service/backend | 11 |
| [raíz] | 9 |
| docs/audit | 8 |
| scripts | 7 |
| backend/docs | 5 |
| backend | 4 |
| .github/workflows | 2 |
| docs/adr | 2 |
| .github | 1 |
| .github/.maven | 1 |
| frontend/public | 1 |
| frontend/scripts | 1 |
| ops/grafana | 1 |
| ops/prometheus | 1 |

## Cómo usar este resultado

1. Lee este resumen primero; no pegues automáticamente todos los archivos en el modelo.
2. Consulta `01-java-index.md` o `02-angular-index.md` para localizar clases, rutas y llamadas relevantes.
3. Consulta `03-config-tests.md` y `04-todos.md` antes de planificar cambios.
4. Para una tarea concreta, pide al agente que lea solo los documentos y archivos del módulo afectado y que verifique las relaciones antes de editar.
5. Los índices son pistas heurísticas, no un análisis semántico completo; valida las rutas y dependencias contra el código.
