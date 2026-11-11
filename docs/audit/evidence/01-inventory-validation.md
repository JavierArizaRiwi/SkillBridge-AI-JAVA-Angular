# Informe de Validación Técnica del Inventario de Repositorio — SkillBridge AI

**Destino de evidencia:** `docs/audit/evidence/01-inventory-validation.md`  
**Rol del auditor:** Auditor técnico especializado en herramientas de inventario de repositorios y arquitectura hexagonal  
**Alcance exclusivo:** Validación técnica del script [scripts/repo_audit.py](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/scripts/repo_audit.py), los índices en [docs/audit/generated/](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/audit/generated/) y el comportamiento ante arquitectura hexagonal y stack tecnológico.

---

## 1. Registro de Metadatos y Contexto de Ejecución

Los siguientes comandos fueron ejecutados en la raíz del espacio de trabajo `/home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular`:

```bash
date -u
git branch --show-current
git rev-parse HEAD
git status --short
sha256sum scripts/repo_audit.py
```

### Resultados observados

| Variable | Valor observado |
|---|---|
| **Fecha UTC** | `2026-10-21T19:08:30Z` |
| **Rama Git activa** | `develop` |
| **Commit HEAD** | `cdd009aa8c9ba351fa8bb50bb1ad0a381e259793` |
| **SHA-256 de `scripts/repo_audit.py`** | `502a369be6bb5a3e67e252903182349c6484dd6b1c371e9018fa179695f59a50` |

### Salida de `git status --short`

```text
AM docs/audit/findings.md
A  scripts/repo_audit.py
?? docs/audit/evidence/
?? docs/audit/generated/
```

- **Observación de estado:** No se realizaron commits, stash, checkout ni modificaciones al código fuente de la aplicación, scripts ni archivos generados durante esta auditoría.

---

## 2. Inspección del Script `scripts/repo_audit.py` con Numeración de Líneas

Se analizó exhaustivamente el archivo [scripts/repo_audit.py](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/scripts/repo_audit.py) (399 líneas en total).

### 2.1. Definición de `EXCLUDED_DIRS` (Líneas 20-25)

```python
20: EXCLUDED_DIRS = {
21:     ".git", "node_modules", "target", "dist", "build", "coverage", ".angular",
22:     ".gradle", "out", "bin", "obj", ".idea", ".vscode", "__pycache__",
23:     ".pytest_cache", ".mypy_cache", ".venv", "venv", "vendor", "playwright-report",
24:     "test-results", "tmp", "temp", "logs", ".next", ".nuxt", ".turbo",
25: }
```

- **Línea crítica:** Línea 22 incluye el literal `"out"`.

### 2.2. Lógica de Poda de Directorios en `walk_files` (Líneas 108-136)

```python
108: def walk_files(root: Path, out_dir: Path, max_files: int) -> tuple[list[Path], bool]:
109:     all_files: list[Path] = []
110:     truncated = False
111:     out_resolved = out_dir.resolve()
112:     for current, dirs, files in os.walk(root, topdown=True, followlinks=False):
113:         current_path = Path(current)
114:         filtered = []
115:         for d in dirs:
116:             if d in EXCLUDED_DIRS:
117:                 continue
118:             candidate = (current_path / d).resolve()
119:             try:
120:                 candidate.relative_to(out_resolved)
121:                 continue  # don't rescan generated reports
122:             except ValueError:
123:                 pass
124:             if (current_path / d).is_symlink():
125:                 continue
126:             filtered.append(d)
127:         dirs[:] = filtered
128:         for filename in files:
129:             path = current_path / filename
130:             if path.is_symlink() or is_sensitive(path):
131:                 continue
132:             all_files.append(path)
133:             if len(all_files) >= max_files:
134:                 truncated = True
135:                 return all_files, truncated
136:     return all_files, truncated
```

### 2.3. Mecanismo de Exclusión de Carpetas Llamadas `out`

1. En la línea 112, `os.walk(root, topdown=True, followlinks=False)` inspecciona de forma recursiva cada directorio.
2. En la línea 115, itera sobre los nombres de carpetas hijos `d in dirs`.
3. En la línea 116 (`if d in EXCLUDED_DIRS: continue`), evalúa si el nombre del subdirectorio pertenece al conjunto global `EXCLUDED_DIRS`.
4. En la línea 127 (`dirs[:] = filtered`), modifica la lista de directorios in-place. Cualquier nombre excluido no será descendido por `os.walk`.
5. **Impacto técnico:** La regla evalúa el nombre simple de la carpeta (`d`), no su ruta relativa al root (`current_path / d`). Por lo tanto, cuando `os.walk` entra a cualquier nivel que contiene una carpeta llamada `out` (como `.../application/port/out` o `.../infrastructure/adapter/out`), la carpeta `out` es descartada globalmente.

### 2.4. Filtro Colateral de Sensibilidad en `is_sensitive` (Líneas 70-78)

```python
70: def is_sensitive(path: Path) -> bool:
71:     name = path.name.lower()
72:     if name in SENSITIVE_PARTS or any(name.startswith(part) for part in (".env",)):
73:         return True
74:     if path.suffix.lower() in SENSITIVE_SUFFIXES:
75:         return True
76:     if any(part in name for part in ("credential", "secret", "service-account")):
77:         return True
78:     return False
```

- **Línea 76:** La comprobación de subcadena `any(part in name for part in ("credential", ...))` descarta cualquier archivo cuyo nombre contenga la palabra `"credential"`. Como consecuencia imprevista, la clase de excepción de dominio [InvalidCredentialsException.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/domain/exception/InvalidCredentialsException.java) fue catalogada como secreto y excluida del inventario.
- **Línea 72:** La regla `name.startswith(".env")` excluye archivos de plantilla como `.env.example` y `audit-service/.env.example`.

---

## 3. Contraste de Rutas Rastreadas por Git vs. `docs/audit/generated/files.csv`

### 3.1. Comando Ejecutado y Métricas Globales

```bash
git ls-files | wc -l
wc -l docs/audit/generated/files.csv
git ls-files '*adapter/out*' '*port/out*' | wc -l
```

- Total de rutas rastreadas por Git en el índice: **385**
- Total de líneas en [files.csv](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/audit/generated/files.csv): **294** (1 encabezado + **293** registros de archivos)
- Diferencia total neta: **92** archivos rastreados ausentes del CSV
- Archivos rastreados bajo patrones `*/adapter/out/*` y `*/port/out/*`: **78**
- Archivos rastreados bajo `*/adapter/out/*` y `*/port/out/*` presentes en [files.csv](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/audit/generated/files.csv): **0**
- Archivos rastreados bajo `*/adapter/out/*` y `*/port/out/*` ausentes de [files.csv](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/audit/generated/files.csv): **78** (100% de exclusión)

### 3.2. Desglose Estructural de los 78 Archivos Excluidos de Arquitectura Hexagonal

Se ejecutó un análisis de partición sobre las 78 rutas:

```python
# Conteo por subárbol arquitectónico
main_adapter_out: 35
main_port_out:    32
test_adapter_out: 10
test_port_out:     1
Total:            78
```

Todos los 78 archivos son de extensión `.java`.

#### A. Puertos de Salida (`backend/src/main/java/.../port/out/` — 32 archivos)
1. `backend/src/main/java/com/riwi/skillbridge/application/port/out/AiRecommendationPort.java`
2. `backend/src/main/java/com/riwi/skillbridge/application/port/out/AiStructuredResponse.java`
3. `backend/src/main/java/com/riwi/skillbridge/application/port/out/BookingEventPublisherPort.java`
4. `backend/src/main/java/com/riwi/skillbridge/application/port/out/BookingRepositoryPort.java`
5. `backend/src/main/java/com/riwi/skillbridge/application/port/out/BookingStatusHistoryPort.java`
6. `backend/src/main/java/com/riwi/skillbridge/application/port/out/CategoryRepositoryPort.java`
7. `backend/src/main/java/com/riwi/skillbridge/application/port/out/NotificationMessage.java`
8. `backend/src/main/java/com/riwi/skillbridge/application/port/out/NotificationPublisherPort.java`
9. `backend/src/main/java/com/riwi/skillbridge/application/port/out/NotificationSenderPort.java`
10. `backend/src/main/java/com/riwi/skillbridge/application/port/out/NotificationType.java`
11. `backend/src/main/java/com/riwi/skillbridge/application/port/out/OfferingCachePort.java`
12. `backend/src/main/java/com/riwi/skillbridge/application/port/out/OfferingRepositoryPort.java`
13. `backend/src/main/java/com/riwi/skillbridge/application/port/out/PasswordHasherPort.java`
14. `backend/src/main/java/com/riwi/skillbridge/application/port/out/PaymentEventPublisherPort.java`
15. `backend/src/main/java/com/riwi/skillbridge/application/port/out/PaymentPort.java`
16. `backend/src/main/java/com/riwi/skillbridge/application/port/out/PaymentResultRepositoryPort.java`
17. `backend/src/main/java/com/riwi/skillbridge/application/port/out/PermanentNotificationException.java`
18. `backend/src/main/java/com/riwi/skillbridge/application/port/out/SpeechToTextPort.java`
19. `backend/src/main/java/com/riwi/skillbridge/application/port/out/TextToSpeechPort.java`
20. `backend/src/main/java/com/riwi/skillbridge/application/port/out/TokenPort.java`
21. `backend/src/main/java/com/riwi/skillbridge/application/port/out/UserAccountPort.java`
22. `backend/src/main/java/com/riwi/skillbridge/application/port/out/UserAdminPort.java`
23. `backend/src/main/java/com/riwi/skillbridge/application/port/out/UserRepositoryPort.java`
24. `backend/src/main/java/com/riwi/skillbridge/application/port/out/VisionPort.java`
25. `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/AuditEventPublisherPort.java`
26. `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BookingCancelledPayload.java`
27. `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BookingCreatedPayload.java`
28. `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BusinessEvent.java`
29. `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/PaymentApprovedPayload.java`
30. `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/PaymentRejectedPayload.java`
31. `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/RecommendationGeneratedPayload.java`
32. `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/RecommendationRequestedPayload.java`

#### B. Adaptadores de Salida (`backend/src/main/java/.../adapter/out/` — 35 archivos)
1. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/ElevenLabsTextToSpeechAdapter.java`
2. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapter.java`
3. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiSpeechToTextAdapter.java`
4. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiVisionAdapter.java`
5. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapter.java`
6. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/NoOpTextToSpeechAdapter.java`
7. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapter.java`
8. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java`
9. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java`
10. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java`
11. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java`
12. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java`
13. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSender.java`
14. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/SimulatedNotificationSender.java`
15. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/InMemoryPaymentResultRepositoryAdapter.java`
16. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/SimulatedPaymentAdapter.java`
17. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignClient.java`
18. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignConfig.java`
19. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapter.java`
20. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentIntentResponse.java`
21. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapter.java`
22. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingStatusHistoryPersistenceAdapter.java`
23. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/CategoryPersistenceAdapter.java`
24. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/OfferingPersistenceAdapter.java`
25. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/UserPersistenceAdapter.java`
26. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingEntity.java`
27. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingStatusHistoryEntity.java`
28. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/CategoryEntity.java`
29. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/OfferingEntity.java`
30. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/UserEntity.java`
31. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingRepository.java`
32. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingStatusHistoryRepository.java`
33. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaCategoryRepository.java`
34. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaOfferingRepository.java`
35. `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaUserRepository.java`

#### C. Pruebas de Puertos y Adaptadores de Salida (11 archivos)
- **Prueba en `port/out` (1 archivo):**
  1. `backend/src/test/java/com/riwi/skillbridge/application/port/out/NotificationMessageTest.java`
- **Pruebas en `adapter/out` (10 archivos):**
  2. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapterTest.java`
  3. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapterTest.java`
  4. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/MockAiAdapter.java`
  5. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapterTest.java`
  6. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java`
  7. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSenderTest.java`
  8. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/SimulatedPaymentAdapterTest.java`
  9. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapterTest.java`
  10. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapterTest.java`
  11. `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java`

### 3.3. Conciliación Completa de los 92 Archivos Ausentes del CSV

Para no limitarse a repetir afirmaciones previas, se programó y ejecutó una conciliación exhaustiva entre `git ls-files` y `files.csv`. El universo total de 92 omisiones se particiona exactamente en 5 grupos sin residuos:

| Grupo | Causa raíz técnica en el inventario o repositorio | Cantidad | Estado |
|---|---|---|---|
| **1. `adapter/out` y `port/out`** | Línea 22 y 116 de `repo_audit.py`: `"out"` en `EXCLUDED_DIRS` | **78** | **CONFIRMADO** |
| **2. Excepción de dominio** | Línea 76 de `repo_audit.py`: `is_sensitive` detecta `"credential"` en `InvalidCredentialsException.java` | **1** | **CONFIRMADO** |
| **3. Plantillas de entorno** | Línea 72 de `repo_audit.py`: `is_sensitive` detecta prefijo `.env` en `.env.example` y `audit-service/.env.example` | **2** | **CONFIRMADO** |
| **4. Compilados en Git** | Directorio `audit-service/backend/target/...` rastreado por Git erróneamente; excluido legítimamente por `"target"` en línea 21 | **10** | **CONFIRMADO** |
| **5. Hallazgos en staging** | `docs/audit/findings.md` staged en Git tras la corrida inicial de las 13:09 | **1** | **CONFIRMADO** |
| **Total conciliado** | Suma exacta de diferencias | **92** | *(Residuo no explicado: 0)* |

### 3.4. Evaluación de Hallazgos Previos (AUD-INV-005 y AUD-INV-006)

1. **AUD-INV-005:**
   - La cifra de 78 archivos de `adapter/out` y `port/out` excluidos queda **CONFIRMADA**.
   - Con respecto a "33 puertos y 11 pruebas": el árbol `port/out` contiene exactamente 32 archivos en `src/main` + 1 archivo en `src/test` (= 33 archivos), aunque no todos son interfaces puras de puerto (incluye DTOs/payloads de eventos y enums). El conteo de pruebas de adaptadores de salida es de 10 en `adapter/out` + 1 en `port/out` (= 11 archivos de prueba).
2. **AUD-INV-006:**
   - Se verificó `git ls-files -- '*.java' | wc -l`: arroja **229** archivos `.java` rastreados por Git, **no 228**.
   - En [files.csv](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/audit/generated/files.csv) existen exactamente **150** archivos `.java`.
   - La diferencia real en código Java es $229 - 150 = 79$ archivos `.java` omitidos (78 de `adapter/out` y `port/out` + 1 de `InvalidCredentialsException.java`).

---

## 4. Investigación de la Discrepancia de `JpaUserRepository`

### 4.1. El Problema Planteado
Se observó en revisiones previas que [`JpaUserRepository`](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaUserRepository.java) figuraba en [01-java-index.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/audit/generated/01-java-index.md), a pesar de que el archivo `JpaUserRepository.java` estaba completamente ausente de [files.csv](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/audit/generated/files.csv).

### 4.2. Análisis del Generador de Informes

1. **Generación de `files.csv` (Líneas 204 y 385-388):**
   `files.csv` se construye a partir de la lista `files` devuelta por `walk_files(root, out_dir, ...)`. Dado que la carpeta `backend/.../adapter/out` fue descartada por `EXCLUDED_DIRS`, el archivo `JpaUserRepository.java` nunca fue incluido en `files`.
2. **Generación de `01-java-index.md` (Líneas 231 y 147-168):**
   `collect_markers` recibe la misma lista `files`. **En ningún momento abrió ni procesó el archivo físico `JpaUserRepository.java`**.
3. **Origen de la coincidencia en `01-java-index.md`:**
   Al buscar el texto en el índice generado (`grep -n "JpaUserRepository" docs/audit/generated/*`), se halló únicamente en la línea 214:
   ```markdown
   | backend/src/main/java/com/riwi/skillbridge/infrastructure/security/DatabaseUserDetailsService.java | 12 | public DatabaseUserDetailsService(JpaUserRepository users) { this.users = users; } |
   ```
4. **Causa real de la captura:**
   - El archivo analizado es [DatabaseUserDetailsService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/DatabaseUserDetailsService.java), ubicado en `infrastructure/security` (directorio que sí fue recorrido).
   - En `JAVA_PATTERNS["Security markers"]` (líneas 51-53), existe la expresión regular:
     ```python
     SecurityFilterChain|authorizeHttpRequests|requestMatchers|@EnableMethodSecurity|@PreAuthorize|@PostAuthorize|@Secured|PasswordEncoder|OncePerRequestFilter|UserDetailsService|AuthenticationProvider|Jwt
     ```
   - La línea 12 de `DatabaseUserDetailsService.java` es el constructor:
     ```java
     public DatabaseUserDetailsService(JpaUserRepository users) { this.users = users; }
     ```
   - Dado que el constructor contiene el nombre de la clase `DatabaseUserDetailsService`, hizo match con el patrón `UserDetailsService`.
   - El script extrajo toda la línea de código e imprimió el snippet completo en la tabla markdown, donde incidentalmente aparece el tipo del parámetro `(JpaUserRepository users)`.

### 4.3. Conclusión sobre `JpaUserRepository`
- **Estado: CONFIRMADO.**
- La presencia de la cadena `JpaUserRepository` en `01-java-index.md` fue un falso positivo de indexación: el archivo `JpaUserRepository.java` **nunca fue indexado** ni reconocido como `@Repository` o `JpaRepository`.
- **Distinción entre defecto del inventario y defecto arquitectónico de la aplicación:**
  - *Defecto del inventario:* El script omitió la interfaz `JpaUserRepository.java` por estar bajo `adapter/out`.
  - *Defecto arquitectónico de la aplicación:* La clase `DatabaseUserDetailsService` (perteneciente a seguridad/inbound) tiene una violación de arquitectura hexagonal al inyectar directamente la interfaz Spring Data `JpaUserRepository` de la capa de persistencia externa en lugar de depender del puerto de dominio `UserRepositoryPort` o del caso de uso correspondiente.

---

## 5. Detección del Stack Tecnológico en los Índices Generados

Se evaluó si los índices generados reconocen de manera adecuada MongoDB, Redis, Kafka, RabbitMQ, OpenFeign y las pruebas de adaptadores.

### 5.1. MongoDB
- **Archivos reales en el repositorio:**
  - Documento: [BusinessEvent.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/model/BusinessEvent.java#L24-L26) (`@Document(collection = "audit_events")`)
  - Repositorio: [AuditEventRepository.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/repository/AuditEventRepository.java#L8) (`extends MongoRepository<BusinessEvent, UUID>`)
  - Inicializador de índices: [AuditMongoInitializer.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java#L28-L30) (configuración programática de esquemas/índices vía `ApplicationRunner`)
  - Prueba: [AuditEventRepositoryTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/test/java/com/riwi/audit/repository/AuditEventRepositoryTest.java)
- **Comportamiento en los índices:**
  - `AuditEventRepository.java` está en `files.csv`, pero **no figura en `01-java-index.md`** bajo *"Persistence / transactions"* porque el regex de la línea 48 no incluye `MongoRepository` ni `@Document`.
  - `BusinessEvent.java` aparece en el índice Java únicamente porque la anotación `@Id` en su línea 26 coincide con el regex genérico de persistencia.
  - `AuditMongoInitializer.java` no es detectado en `03-config-tests.md` bajo migraciones de base de datos porque la regla (línea 256) busca solo SQL y subrutas `/migration/`.
- **Clasificación:** **INADECUADO (CONFIRMADO)**. MongoDB es prácticamente invisible en los índices de infraestructura.

### 5.2. Redis
- **Archivos reales en el repositorio:**
  - Adaptador: [RedisOfferingCacheAdapter.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java#L16-L18) (implementa `OfferingCachePort` usando `StringRedisTemplate`)
  - Puerto: [OfferingCachePort.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/port/out/OfferingCachePort.java)
- **Comportamiento en los índices:**
  - Ambos archivos están ausentes de `files.csv` y de `01-java-index.md` por encontrarse bajo carpetas `out`.
  - Adicionalmente, `JAVA_PATTERNS` carece de patrones para Redis (`RedisTemplate`, `StringRedisTemplate`, `@Cacheable`).
- **Clasificación:** **INADECUADO (CONFIRMADO)**. Totalmente omitido del inventario e índices.

### 5.3. Kafka
- **Archivos reales en el repositorio:**
  - Productores (salida):
    - [KafkaAuditEventPublisher.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java)
    - [KafkaBookingEventPublisher.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java)
  - Consumidores (entrada):
    - [KafkaAuditConsumer.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaAuditConsumer.java#L11-L15)
    - [KafkaAuditConsumer.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java#L11-L15)
- **Comportamiento en los índices:**
  - Los productores en `adapter/out` están excluidos al 100%.
  - Los consumidores en `adapter/in` y en `audit-service` están en `files.csv`, pero en `01-java-index.md` solo aparecen clasificados genéricamente bajo `@Component`. La anotación `@KafkaListener` no existe en `JAVA_PATTERNS`, por lo que el rol de Kafka como bus de mensajería no es detectado.
- **Clasificación:** **INADECUADO (CONFIRMADO)**.

### 5.4. RabbitMQ
- **Archivos reales en el repositorio:**
  - Configuración: [RabbitConfiguration.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java)
  - Publicadores (salida):
    - [RabbitNotificationPublisher.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java)
    - [RabbitPaymentEventPublisher.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java)
  - Consumidores (entrada):
    - [NotificationConsumer.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationConsumer.java)
    - [PaymentResultConsumer.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java)
  - Prueba: [RabbitNotificationPublisherTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java)
- **Comportamiento en los índices:**
  - Los publicadores y la prueba de integración de RabbitMQ fueron excluidos por estar bajo `adapter/out`.
  - `RabbitConfiguration.java` solo se indexa por `@Configuration` y `@Bean`.
  - `@RabbitListener` en los consumidores no está contemplado en `JAVA_PATTERNS`.
- **Clasificación:** **INADECUADO (CONFIRMADO)**.

### 5.5. OpenFeign
- **Archivos reales en el repositorio:**
  - Activación: [SkillBridgeApplication.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/SkillBridgeApplication.java#L11) (`@EnableFeignClients`)
  - Cliente HTTP: [StripeFeignClient.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignClient.java#L11) (`@FeignClient(...)`)
  - Configuración: [StripeFeignConfig.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignConfig.java)
- **Comportamiento en los índices:**
  - El cliente y su configuración están bajo `adapter/out`, por lo que no existen en `files.csv` ni en `01-java-index.md`.
  - `@EnableFeignClients` en la clase principal no tiene regla de búsqueda en `JAVA_PATTERNS`. La palabra `feign` no aparece en ningún reporte generado.
- **Clasificación:** **INADECUADO (CONFIRMADO)**.

### 5.6. Pruebas de Adaptadores
- **Archivos reales en el repositorio:**
  - Adaptadores de salida: **10** pruebas en `backend/src/test/.../adapter/out/` (persistencia, IA, Stripe, mensajería, notificación).
  - Puertos de salida: **1** prueba en `backend/src/test/.../port/out/NotificationMessageTest.java`.
  - Adaptadores de entrada: **9** pruebas en `backend/src/test/.../adapter/in/` (REST, controllers, filtros, consumidores).
- **Comportamiento en los índices:**
  - Las 9 pruebas de entrada aparecen listadas en [03-config-tests.md](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/docs/audit/generated/03-config-tests.md).
  - Las 11 pruebas asociadas a adaptadores y puertos de salida están **100% ausentes** de `files.csv` y `03-config-tests.md` debido a la poda de carpetas `out`.
  - El índice `03-config-tests.md` no clasifica las pruebas por nivel arquitectónico (unitarias, integración, adaptadores, controladores o dominio).
- **Clasificación:** **INADECUADO (CONFIRMADO)**.

---

## 6. Recomendaciones: Modificación Mínima y Pruebas Offline

> [!IMPORTANT]
> Siguiendo estrictamente las reglas de la auditoría, **no se aplicó ninguna modificación** a `scripts/repo_audit.py`, archivos generados ni código del proyecto. Las siguientes recomendaciones representan la solución técnica mínima no disruptiva.

### 6.1. Modificación Mínima Recomendada en `scripts/repo_audit.py`

#### Cambio 1: Desacoplar la exclusión de carpetas `out`
- **Problema:** La presencia del nombre simple `"out"` en `EXCLUDED_DIRS` (línea 22) poda cualquier carpeta hexagonal `adapter/out` o `port/out`.
- **Solución mínima:**
  1. Eliminar `"out"` de `EXCLUDED_DIRS`.
  2. En `walk_files`, si se desea ignorar la carpeta de salida de compilación de IDEs como IntelliJ (`./out` en la raíz del proyecto), verificar explícitamente la relación de ruta con la raíz:
     ```python
     # En walk_files: excluir 'out' únicamente si es hijo directo de la raíz del proyecto
     if d == "out" and current_path == root:
         continue
     ```

#### Cambio 2: Evitar falsos positivos en `is_sensitive`
- **Problema:** La búsqueda por subcadena `"credential"` (línea 76) descarta la clase Java `InvalidCredentialsException.java`.
- **Solución mínima:**
  Condicionar la regla de nombres de secretos para no descartar archivos fuente de código:
  ```python
  if path.suffix.lower() not in {".java", ".kt", ".ts", ".js"}:
      if any(part in name for part in ("credential", "secret", "service-account")):
          return True
  ```

#### Cambio 3: Incorporar categorías para Mensajería, Feign y NoSQL
- Ampliar `JAVA_PATTERNS`:
  ```python
  "Persistence / transactions": re.compile(
      r"@Entity\b|@Table\b|@Id\b|@EmbeddedId\b|@Repository\b|@Query\b|@Transactional\b|JpaRepository|CrudRepository|EntityManager|@Document\b|MongoRepository|RedisTemplate|StringRedisTemplate"
  ),
  "Messaging & Integration / Clients": re.compile(
      r"@KafkaListener\b|KafkaTemplate\b|@RabbitListener\b|RabbitTemplate\b|@FeignClient\b|@EnableFeignClients\b"
  ),
  ```

### 6.2. Protocolo de Pruebas Offline para Verificación

Para validar la corrección una vez aplicada, se recomienda ejecutar el siguiente plan de pruebas offline en un entorno aislado sin conexión a red:

1. **Ejecución dirigida a directorio temporal aislado:**
   ```bash
   python3 scripts/repo_audit.py --root . --out /tmp/repo_audit_validation
   ```
2. **Prueba automatizada de conciliación Git vs. CSV:**
   ```python
   # Script de aserción offline
   import csv, subprocess

   with open('/tmp/repo_audit_validation/files.csv') as f:
       csv_files = set(row[0] for row in csv.reader(f))

   git_out_files = set(subprocess.check_output(
       ['git', 'ls-files', '*adapter/out*', '*port/out*'], text=True
   ).splitlines())

   missing_out = git_out_files - csv_files
   assert len(missing_out) == 0, f"Aún faltan {len(missing_out)} archivos bajo out"
   assert "backend/src/main/java/com/riwi/skillbridge/domain/exception/InvalidCredentialsException.java" in csv_files, "InvalidCredentialsException sigue excluida"
   print("PRUEBA EXITOSA: Cobertura completa de adaptadores, puertos y excepciones.")
   ```
3. **Validación de índices generados:**
   - Verificar presencia de `StripeFeignClient` en `/tmp/repo_audit_validation/01-java-index.md`.
   - Verificar presencia de las 10 pruebas de adaptadores en `/tmp/repo_audit_validation/03-config-tests.md`.
   - Verificar que archivos sensibles reales (como `.env` o certificados `.pem`) permanezcan excluidos.

---

## 7. Matriz de Conclusiones, Limitaciones y Pruebas Pendientes

### 7.1. Matriz de Conclusiones Clasificadas

| ID | Declaración / Conclusión | Clasificación | Evidencia Técnica Clave |
|---|---|---|---|
| **CONC-01** | `scripts/repo_audit.py` excluyó exactamente 78 archivos rastreados bajo `adapter/out` y `port/out`. | **CONFIRMADO** | `git ls-files '*adapter/out*' '*port/out*'` arroja 78 rutas; 0 están en `docs/audit/generated/files.csv`. Líneas 22 y 116 de `repo_audit.py`. |
| **CONC-02** | Git rastrea 229 archivos `.java`, mientras `files.csv` solo registró 150 archivos `.java` (diferencia: 79). | **CONFIRMADO** | 78 archivos pertenecen a `out` y 1 a `InvalidCredentialsException.java`. La cifra previa de 228 fue un subconteo de 1 unidad. |
| **CONC-03** | La clase `InvalidCredentialsException.java` fue omitida por falso positivo de detección de secretos. | **CONFIRMADO** | Línea 76 de `repo_audit.py`: subcadena `"credential"` en el nombre del archivo. |
| **CONC-04** | La aparición de `JpaUserRepository` en `01-java-index.md` no se debe a su análisis como archivo fuente, sino a una captura incidental en `DatabaseUserDetailsService.java:12`. | **CONFIRMADO** | `01-java-index.md:214`. Coincidencia con regex `UserDetailsService` en el constructor que inyecta `JpaUserRepository`. El archivo del repositorio nunca fue leído. |
| **CONC-05** | `DatabaseUserDetailsService` presenta una violación arquitectónica al inyectar directamente `JpaUserRepository`. | **CONFIRMADO** | Líneas 3 y 10-12 de `DatabaseUserDetailsService.java`: acoplamiento directo entre capa de seguridad y repositorio Spring Data de salida sin pasar por puerto. |
| **CONC-06** | El inventario es inadecuado para identificar MongoDB, Redis, Kafka, RabbitMQ, OpenFeign y las 11 pruebas de adaptadores de salida. | **CONFIRMADO** | Ausencia total de Feign y Redis en índices; omisión de `@KafkaListener` y `@RabbitListener`; omisión del 100% de pruebas en `adapter/out`. |
| **CONC-07** | El origen del literal `"out"` en `EXCLUDED_DIRS` fue concebido para carpetas de compilación IDE (tipo IntelliJ IDEA) y no para arquitectura hexagonal. | **HIPÓTESIS** | `"out"` aparece agrupado junto a `"bin"`, `"obj"`, `".idea"`, `".vscode"`. No se contempló la convención hexagonal `adapter/out` y `port/out`. |
| **CONC-08** | La presencia de 10 archivos bajo `audit-service/backend/target/` rastreados por Git corresponde a un error de versionado al hacer commit de binarios. | **CONFIRMADO** | Git rastrea archivos `.jar` y `.class` en `audit-service/backend/target/`. El inventario los excluye correctamente vía `"target"`. |

### 7.2. Limitaciones de esta Auditoría
- **Inmutabilidad del entorno:** Conforme a las reglas de la auditoría, no se ejecutaron escrituras en archivos productivos ni se corrió el script con correcciones en las rutas de entrega.
- **Alcance textual:** Las comprobaciones de regex en el inventario analizan patrones basados en líneas de texto y no en un Abstract Syntax Tree (AST) de Java o TypeScript, por lo que anotaciones multilínea o declaraciones complejas están sujetas a limitaciones heurísticas inherentes.

### 7.3. Pruebas Pendientes
1. **Ejecución y regeneración formal:** Ejecutar formalmente la modificación mínima de `scripts/repo_audit.py` y actualizar `docs/audit/generated/` cuando las reglas de la auditoría lo autoricen.
2. **Refactorización arquitectónica de `DatabaseUserDetailsService`:** Separar el acoplamiento directo a `JpaUserRepository` migrándolo hacia el puerto correspondiente `UserRepositoryPort`.
3. **Limpieza del índice Git en `audit-service`:** Ejecutar `git rm -r --cached audit-service/backend/target/` para retirar del árbol de Git los binarios compilados.
