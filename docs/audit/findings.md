# Registro de hallazgos — Auditoría SkillBridge AI

> Estado inicial preparado a partir del script adjunto y del informe pegado por el usuario. Los hallazgos referidos al repositorio local deben cotejarse con `evidence/01-inventory-gap.md` antes de subirlos como evidencia definitiva. No se han modificado archivos de aplicación.

| ID | Hallazgo | Estado | Evidencia / siguiente verificación |
|---|---|---|---|
| AUD-INV-001 | La copia adjunta del script excluye globalmente directorios llamados `out`, lo que puede ocultar código fuente en `adapter/out` y `port/out`. | CONFIRMADO en la copia adjunta; pendiente en copia local | `01-inventory-gap.md`, sección 3. Comparar SHA-256 local y listar rutas tracked con `git ls-files '*adapter/out*' '*port/out*'`. |
| AUD-INV-002 | La copia adjunta no clasifica explícitamente patrones de MongoDB y Redis en la categoría de persistencia. | CONFIRMADO en la copia adjunta; pendiente en copia local | `01-inventory-gap.md`, sección 4. Verificar líneas locales y buscar implementaciones reales. |
| AUD-INV-003 | La copia adjunta carece de categoría explícita para listeners/productores Kafka/RabbitMQ y clientes `@FeignClient`. | CONFIRMADO en la copia adjunta; pendiente en copia local | `01-inventory-gap.md`, sección 4. Verificar código local y cobertura de los índices. |
| AUD-INV-004 | La copia adjunta detecta migraciones principalmente por rutas y nombres SQL; no detecta explícitamente clases `*MongoInitializer.java`. | CONFIRMADO en la copia adjunta; pendiente en copia local | `01-inventory-gap.md`, sección 4. Verificar líneas locales y existencia de inicializadores. |
| AUD-INV-005 | El inventario local supuestamente omitió 78 archivos, 33 puertos y 11 pruebas de adaptadores. | PENDIENTE DE VERIFICACIÓN | Son cifras del informe previo, no reproducidas independientemente aquí. Ejecutar la comparación de conjuntos descrita en `01-inventory-gap.md`. |
| AUD-INV-006 | La afirmación de que Git rastrea exactamente 228 archivos `.java` no ha sido reproducida. | PENDIENTE DE VERIFICACIÓN | Ejecutar `git ls-files -- '*.java' | wc -l` y guardar la salida. No comparar solo números: comprobar qué rutas faltan del CSV. |

## Convención de estados

- **CONFIRMADO:** reproducido con evidencia directa. Si el alcance dice “copia adjunta”, no implica automáticamente que la copia local sea idéntica.
- **HIPÓTESIS:** explicación plausible aún no demostrada.
- **PENDIENTE DE VERIFICACIÓN:** no se ha ejecutado la comprobación necesaria o no se tiene acceso a la evidencia local.
- **RESUELTO:** la corrección está aplicada y una comprobación posterior demuestra que el hallazgo dejó de reproducirse.
- **DESCARTADO:** la evidencia contradice el hallazgo original.

## Cambios de estado

No marcar un hallazgo como `RESUELTO` al implementar la corrección solamente; adjuntar la comprobación posterior y su resultado. Mantener los IDs estables aunque cambie la redacción.
