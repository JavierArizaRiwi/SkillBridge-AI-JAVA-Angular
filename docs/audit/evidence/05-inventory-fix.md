# Evidencia de Auditoría: Corrección Mínima del Script de Inventario (`repo_audit.py`)

## 1. Metadatos de la Ejecución y Baseline

- **Fecha UTC:** 2026-10-21T21:55:00Z
- **Fecha local:** 2026-10-21T16:55:00-05:00
- **Rama Git activa:** `develop`
- **Commit auditado:** `cdd009aa8c9ba351fa8bb50bb1ad0a381e259793`
- **Mensaje de commit HEAD:** `Merge pull request #73 from Danilo-Doria/Bugfix/BUG-04-Categoria`
- **Archivo modificado:** `scripts/repo_audit.py`
- **SHA-256 staged original (baseline):** `502a369be6bb5a3e67e252903182349c6484dd6b1c371e9018fa179695f59a50`
- **SHA-256 en disco post-modificación:** `cb1a6edc73811700cb77796e0cd4ce2af63f1e049cd6e79aa735faba344b91ac`
- **Estado de Git durante la verificación:**
  ```text
  AM docs/audit/findings.md
  AM scripts/repo_audit.py
  ?? docs/audit/evidence/
  ?? docs/audit/generated/
  ```

---

## 2. Objetivo de la Corrección

Aplicar la corrección mínima y quirúrgica sobre `scripts/repo_audit.py` para resolver las omisiones detectadas en la revisión cruzada (evidencia `04-cross-review.md`):
1. **Omisión de paquetes de arquitectura hexagonal:** Exclusión indiscriminada de cualquier directorio llamado `out` (`adapter/out` y `port/out`).
2. **Omisión de clases de dominio por falso positivo de sensibilidad:** Exclusión de clases Java legítimas como `InvalidCredentialsException.java` debido a filtros por subcadena `"credential"`.
3. **Falta de cobertura para tecnologías clave:** Carencia de patrones heurísticos en `JAVA_PATTERNS` para Spring Data MongoDB, Redis (`StringRedisTemplate`), Apache Kafka, RabbitMQ, OpenFeign e inicializadores programáticos (`ApplicationRunner`, `CommandLineRunner`).
4. **Preservación estricta de seguridad y compatibilidad:** Mantener exclusiones de archivos sensibles reales (`.env`, certificados, claves privadas) y de artefactos generados (`target/`, `node_modules/`, `build/`, `dist/`), preservando la interfaz CLI (`--root`, `--out`, `--max-files`).

---

## 3. Modificaciones Realizadas en `scripts/repo_audit.py`

Las modificaciones se ciñeron estrictamente a las cuatro áreas autorizadas (A, B, C, D):

### A. Desacoplamiento de la regla `out` en `EXCLUDED_DIRS` y `walk_files()`
- Se removió `"out"` de la constante `EXCLUDED_DIRS`.
- En `walk_files()`, se incorporó la exclusión contextual condicionada exclusivamente a directorios `out` ubicados en la raíz del proyecto (`current_resolved == root_resolved`), evitando podar subpaquetes de código fuente:
  ```python
  if d == "out" and current_resolved == root_resolved:
      continue
  ```

### B. Corrección del filtro de nombres sensibles en `is_sensitive()`
- Se acotó la comprobación por subcadenas (`"credential"`, `"secret"`, `"service-account"`) para no descartar archivos de código fuente Java (`.java`, `.kt`), preservando intacta la exclusión de secretos reales (`.env`, `.pem`, `.key`, `id_rsa`, `service-account.json`, etc.):
  ```python
  if path.suffix.lower() not in {".java", ".kt"}:
      if any(part in name for part in ("credential", "secret", "service-account")):
          return True
  ```

### C. Ampliación de patrones en `JAVA_PATTERNS` y de inicializadores
- Se agregaron categorías dedicadas y patrones específicos para evitar atribuciones erróneas de comportamiento:
  - **`HTTP clients / OpenFeign`:** `@FeignClient\b|@EnableFeignClients\b|\bRequestInterceptor\b`
  - **`Persistence / transactions`:** Extendido con `@Document\b`, `MongoRepository\b`, `ReactiveMongoRepository\b` y `MongoTemplate\b`.
  - **`Cache / Redis`:** `\b(RedisTemplate|StringRedisTemplate|RedisConnectionFactory)\b|@(Cacheable|CacheEvict|CachePut|EnableCaching)\b`
  - **`Messaging / RabbitMQ`:** `@RabbitListener\b|\bRabbitTemplate\b|\bAmqpAdmin\b|@(QueueBinding|Exchange|Queue)\b`
  - **`Messaging / Kafka`:** `@KafkaListener\b|\bKafkaTemplate\b|\bConsumerRecord\b|\bProducerRecord\b`
  - **`Programmatic initializers / seeders`:** `\bclass\b[^{]*\bimplements\s+[^\{]*\b(ApplicationRunner|CommandLineRunner)\b|^\s*@PostConstruct\b`
  - **`Security markers`:** Refinado con límites de palabra (`\bUserDetailsService\b`, `(?<![A-Za-z0-9_])Jwt`) para evitar falsos positivos en nombres de parámetros o constructores.
- En la detección de migraciones de base de datos (`migration_files`), se incorporaron inicializadores programáticos (`or re.search(r"(initializer|seeder)\.java$", low)`).

### D. Compatibilidad de interfaz CLI
- Se conservaron sin alteración los argumentos `--root`, `--out` y `--max-files`.

---

## 4. Comandos de Verificación Ejecutados y Resultados

### 4.1. Verificación Inicial en Directorio Aislado (`/tmp/repo_audit_val`)

**Comando:**
```bash
python3 scripts/repo_audit.py --out /tmp/repo_audit_val
```
- **Código de salida:** `0`
- **Archivos inventariados:** `386`

### 4.2. Validación del Formato CSV y Columna de Rutas (Verificación 2)

**Comando:**
```bash
head -n 5 /tmp/repo_audit_val/files.csv
```
- **Resultado:**
  ```text
  path,extension,size_bytes,line_count_or_status
  .editorconfig,[sin extensión],141,no analizado
  CHANGELOG.md,.md,5982,160
  PROJECT-STRUCTURE.txt,.txt,1998,79
  LICENSE,[sin extensión],1091,21
  ```
  Formato CSV estándar con codificación UTF-8, cabeceras legibles y columna `path` en índice 0.

### 4.3. Conciliación de Archivos Java: Git vs. Inventario (Verificación 3)

**Script de prueba:**
```python
import csv, subprocess

tracked_java = set(subprocess.check_output(['git', 'ls-files', '--', '*.java'], text=True).splitlines())
with open('/tmp/repo_audit_val/files.csv', mode='r', encoding='utf-8-sig') as f:
    reader = csv.reader(f)
    next(reader)
    inv_java = {row[0] for row in reader if len(row) > 1 and row[1] == '.java'}

print(f"Tracked Java: {len(tracked_java)}")
print(f"Inventoried Java: {len(inv_java)}")
print(f"Diferencia: {tracked_java ^ inv_java}")
```
- **Archivos `.java` rastreados por Git:** **229**
- **Archivos `.java` en el inventario:** **229**
- **Diferencia / omisiones:** **0 archivos** (coincidencia exacta del 100%).

### 4.4. Comprobación de Inclusión de `adapter/out` y `port/out` (Verificación 4)

**Script de prueba:**
```python
import csv, subprocess

tracked_out = {p for p in subprocess.check_output(['git', 'ls-files'], text=True).splitlines() if 'adapter/out' in p or 'port/out' in p}
with open('/tmp/repo_audit_val/files.csv', mode='r', encoding='utf-8-sig') as f:
    reader = csv.reader(f)
    next(reader)
    inv_out = {row[0] for row in reader if 'adapter/out' in row[0] or 'port/out' in row[0]}

print(f"Tracked out: {len(tracked_out)}, Inventoried out: {len(inv_out)}, Faltantes: {tracked_out - inv_out}")
```
- **Archivos rastreados en `adapter/out` o `port/out`:** **78**
- **Archivos inventariados en `adapter/out` o `port/out`:** **78**
- **Faltantes:** **0**

### 4.5. Detección Específica de Clases Clave (Verificación 5)

| Archivo / Componente | Presente en `files.csv` | Tamaño / Líneas | Detección en Informes Markdown | Sección en `01-java-index.md` |
|---|---|---|---|---|
| `InvalidCredentialsException.java` | **SÍ** | 193 B / 5 líneas | N/A (Excepción pura de dominio, sin anotaciones Spring) | N/A |
| `JpaUserRepository.java` | **SÍ** | 472 B / 11 líneas | Detectado (L4, L8) | `Persistence / transactions` |
| `AuditEventRepository.java` | **SÍ** | 579 B / 17 líneas | Detectado (L4, L14) | `Persistence / transactions` |
| `RedisOfferingCacheAdapter.java` | **SÍ** | 2,017 B / 54 líneas | Detectado (L8, L18, L22) | `Cache / Redis` y `Spring entry points` |
| `KafkaAuditEventPublisher.java` | **SÍ** | 1,749 B / 41 líneas | Detectado (L8, L17) | `Messaging / Kafka` y `Spring entry points` |
| `StripeFeignClient.java` | **SÍ** | 1,058 B / 29 líneas | Detectado (L13, L20) | `HTTP clients / OpenFeign` y `HTTP mapping` |

### 4.6. Verificación de Exclusión de Secretos y Archivos Sensibles (Verificación 6)

**Script de prueba:**
```python
import csv
from pathlib import Path

with open('/tmp/repo_audit_val/files.csv', mode='r', encoding='utf-8-sig') as f:
    reader = csv.reader(f)
    next(reader)
    inv_paths = [row[0] for row in reader]

leaks = [p for p in inv_paths if Path(p).name.startswith('.env') or Path(p).suffix.lower() in {'.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.crt'} or Path(p).name in {'id_rsa', 'credentials', 'service-account.json', 'secrets.json'}]
print(f"Fugas sensibles detectadas: {len(leaks)}")
```
- **Resultado:** **0 fugas sensibles**. `.env.example`, plantillas y archivos con extensiones de certificados/claves permanecen excluidos.

### 4.7. Verificación de Exclusión de Artefactos de Compilación (Verificación 7)

**Script de prueba:**
```python
import csv
from pathlib import Path

excluded = {'target', 'node_modules', 'dist', 'build', '.git', '.angular', '.gradle', 'bin', 'obj', '__pycache__', 'test-results', 'coverage'}
with open('/tmp/repo_audit_val/files.csv', mode='r', encoding='utf-8-sig') as f:
    reader = csv.reader(f)
    next(reader)
    inv_paths = [row[0] for row in reader]

leaks = [p for p in inv_paths if set(Path(p).parts).intersection(excluded)]
print(f"Artefactos generados detectados: {len(leaks)}")
```
- **Resultado:** **0 artefactos generados**. Los 10 binarios rastreados en Git bajo `audit-service/backend/target/` fueron excluidos legítimamente por la regla `target`.
- **Código de salida del proceso:** `0`.

---

## 5. Cuadro Comparativo: Antes vs. Después

| Métrica / Verificación | Antes (Línea Base) | Después (`/tmp/repo_audit_val`) | Después (`docs/audit/generated`) |
|---|---|---|---|
| **Archivos inventariados totales** | 293 | 386 | 380 |
| **Archivos `.java` inventariados** | 150 | 229 | 229 |
| **Archivos `.java` rastreados faltantes** | 79 | **0** | **0** |
| **Archivos en `adapter/out` y `port/out`** | 0 | 78 | 78 |
| **`InvalidCredentialsException.java`** | Omitido | **Incluido** | **Incluido** |
| **Coincidencias OpenFeign** | 0 | 4 | 4 |
| **Coincidencias Caché Redis** | 0 | 3 | 3 |
| **Coincidencias Kafka** | Parcial (solo consumers) | 6 (publicadores y consumidores) | 6 |
| **Coincidencias RabbitMQ** | Sin sección dedicada | 23 | 23 |
| **Inicializadores programáticos** | 0 | 2 (`AuditMongoInitializer`, `AdminSeeder`) | 2 |
| **Pruebas en `adapter/out` y `port/out`** | 0 | 11 detectadas | 11 detectadas |

> **Nota sobre el total de archivos inventariados:**
> - En `/tmp/repo_audit_val`: 386 archivos (incluye los archivos de evidencia y generados previamente en `docs/audit/`).
> - En `docs/audit/generated`: 380 archivos (la carpeta `docs/audit/generated` se autoexcluye dinámicamente como `out_dir`).
> - Ambos coinciden de manera idéntica en los **229 archivos Java** rastreados por Git (100% de cobertura).

---

## 6. Regeneración de Informes Definitivos

Una vez superadas todas las pruebas en `/tmp/repo_audit_val`, se ejecutó la regeneración de los informes de auditoría del repositorio:

```bash
python3 scripts/repo_audit.py
```
- **Directorio de salida:** `docs/audit/generated`
- **Código de salida:** `0`
- **Archivos regenerados:**
  - `docs/audit/generated/00-summary.md`
  - `docs/audit/generated/01-java-index.md`
  - `docs/audit/generated/02-angular-index.md`
  - `docs/audit/generated/03-config-tests.md`
  - `docs/audit/generated/04-todos.md`
  - `docs/audit/generated/files.csv`

---

## 7. Limitaciones y Asuntos Pendientes

1. **Higiene de Git en `audit-service/backend/target/`:** Persisten 10 artefactos compilados (incluyendo archivos `.jar`) rastreados por Git en el historial. El script de auditoría los omite correctamente por la regla `target`, pero el repositorio requerirá un `git rm --cached` en la fase correspondiente de DevOps/higiene.
2. **Plantillas de entorno `.env.example`:** Siguen excluidas del inventario porque su nombre comienza con `.env` bajo `is_sensitive()`. Esta conducta preserva el principio de mínima exposición y no afecta el código fuente de la aplicación.
3. **Registro central `docs/audit/findings.md`:** Conforme a las instrucciones, dicho archivo no ha sido actualizado en este paso y se consolidará tras la revisión de esta evidencia.
