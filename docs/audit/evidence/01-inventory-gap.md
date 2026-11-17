# Evidencia de Auditoría: Brecha de Inventario e Inspección Heurística

## 1. Metadatos de la Verificación

- **Fecha UTC:** 2026-10-21T18:50:59+00:00
- **Fecha local:** 2026-10-21T13:50:59-05:00
- **Rama:** `develop`
- **Commit auditado:** `cdd009aa8c9ba351fa8bb50bb1ad0a381e259793`
- **Mensaje del commit:** `Merge pull request #73 from Danilo-Doria/Bugfix/BUG-04-Categoria`
- **Archivo de auditoría analizado:** `scripts/repo_audit.py` (SHA-256: `502a369be6bb5a3e67e252903182349c6484dd6b1c371e9018fa179695f59a50`)
- **Inventario contrastado:** `docs/audit/generated/files.csv` y reportes en `docs/audit/generated/`

---

## 2. Objetivo

Verificar empíricamente mediante inspección de código y comprobaciones de solo lectura:
1. La causa raíz en `scripts/repo_audit.py` que excluye inadvertidamente directorios legítimos de código fuente denominados `out`.
2. El impacto cuantitativo y cualitativo de dicha exclusión en el inventario de arquitectura hexagonal (`port/out` y `adapter/out`).
3. El estado real de implementación en el código de producto para tecnologías declaradas (MongoDB, Redis, RabbitMQ, Kafka y OpenFeign), diferenciando falsos negativos del reporte de auditoría respecto a defectos de la aplicación.

---

## 3. Inspección del Código del Script (`scripts/repo_audit.py`)

### 3.1. Regla de exclusión de directorios `out`
En el script [scripts/repo_audit.py](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/scripts/repo_audit.py):

- **Líneas 20–25:**
```python
20: EXCLUDED_DIRS = {
21:     ".git", "node_modules", "target", "dist", "build", "coverage", ".angular",
22:     ".gradle", "out", "bin", "obj", ".idea", ".vscode", "__pycache__",
23:     ".pytest_cache", ".mypy_cache", ".venv", "venv", "vendor", "playwright-report",
24:     "test-results", "tmp", "temp", "logs", ".next", ".nuxt", ".turbo",
25: }
```
- **Líneas 115–127 (`walk_files`):**
```python
115:         for d in dirs:
116:             if d in EXCLUDED_DIRS:
117:                 continue
118:             candidate = (current_path / d).resolve()
...
126:             filtered.append(d)
127:         dirs[:] = filtered
```

**Mecanismo del defecto:**
`EXCLUDED_DIRS` contiene la cadena literal `"out"`, pensada habitualmente para ignorar la carpeta de artefactos de compilación de IDEs (como IntelliJ IDEA `out/`). Sin embargo, en `walk_files`, la comparación `d in EXCLUDED_DIRS` evalúa únicamente el nombre del segmento de directorio actual (`d`), no su ruta relativa ni si es un directorio de compilación en la raíz. En una arquitectura hexagonal o basada en puertos y adaptadores, los paquetes `port/out` y `adapter/out` tienen un subdirectorio con nombre exacto `out`. En cuanto `os.walk` encuentra dicho nombre, la rama entera es eliminada del array `dirs` in situ, impidiendo que el escáner descienda a ningún puerto o adaptador de salida.

### 3.2. Regla de exclusión por subcadena de sensibilidad (`credential`)
- **Líneas 70–78 (`is_sensitive`):**
```python
70: def is_sensitive(path: Path) -> bool:
71:     name = path.name.lower()
...
76:     if any(part in name for part in ("credential", "secret", "service-account")):
77:         return True
78:     return False
```
- **Líneas 130–131 (`walk_files`):**
```python
130:             if path.is_symlink() or is_sensitive(path):
131:                 continue
```

**Mecanismo del defecto:**
La función `is_sensitive` descarta cualquier archivo cuyo nombre contenga la subcadena `"credential"`. Como consecuencia, descarta clases de código fuente legítimas como `backend/src/main/java/com/riwi/skillbridge/domain/exception/InvalidCredentialsException.java`, tratándolas erróneamente como fugas de secretos o credenciales.

### 3.3. Carencia de patrones heurísticos para persistencia NoSQL, caché, mensajería y clientes HTTP
- **Líneas 41–54 (`JAVA_PATTERNS`):**
```python
41: JAVA_PATTERNS = {
42:     "Spring entry points / layers": re.compile(
43:         r"@SpringBootApplication|@RestController|@Controller\b|@Service\b|@Repository\b|@Component\b|@Configuration\b|@Bean\b"
44:     ),
45:     "HTTP mapping annotations": re.compile(
46:         r"@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|ExceptionHandler)\b"
47:     ),
48:     "Persistence / transactions": re.compile(
49:         r"@Entity\b|@Table\b|@Id\b|@EmbeddedId\b|@Repository\b|@Query\b|@Transactional\b|JpaRepository|CrudRepository|EntityManager"
50:     ),
51:     "Security markers": re.compile(
52:         r"SecurityFilterChain|authorizeHttpRequests|requestMatchers|@EnableMethodSecurity|@PreAuthorize|@PostAuthorize|@Secured|PasswordEncoder|OncePerRequestFilter|UserDetailsService|AuthenticationProvider|Jwt"
53:     ),
54: }
```

**Mecanismo de la omisión en el reporte:**
- **MongoDB:** `JAVA_PATTERNS["Persistence / transactions"]` busca `JpaRepository`, `CrudRepository`, `@Entity`, `@Table`, pero no `MongoRepository`, `ReactiveMongoRepository`, ni `@Document`. Aunque las clases de MongoDB no están en carpetas `out`, la interfaz `AuditEventRepository` que extiende `MongoRepository` no genera coincidencias en el índice.
- **Redis:** No existe ninguna categoría ni patrón para Redis (`RedisTemplate`, `StringRedisTemplate`, `@Cacheable`, `@EnableCaching`).
- **OpenFeign:** No se incluye `@FeignClient` ni `@EnableFeignClients` en `Spring entry points / layers`.
- **Mensajería:** No se incluyen anotaciones `@RabbitListener`, `@KafkaListener`, ni clientes `RabbitTemplate`, `KafkaTemplate`.

---

## 4. Comandos Ejecutados y Resultados Observados

### 4.1. Comparación cuantitativa: Archivos en Git vs. Inventario generado

**Comando ejecutado:**
```bash
git ls-files | wc -l
python3 -c "import csv; f = open('docs/audit/generated/files.csv'); print(len(list(csv.reader(f))) - 1)"
```
**Resultado observado:**
- Archivos rastreados por Git: **384**
- Archivos en `docs/audit/generated/files.csv`: **293**
- Diferencia neta: **91 archivos rastreados por Git no inventariados**.

---

### 4.2. Desglose detallado de los 91 archivos omitidos del inventario

**Comando ejecutado:**
```bash
python3 -c "
import csv, subprocess
with open('docs/audit/generated/files.csv') as f:
    inv = {row[0] for row in csv.reader(f)}
tracked = set(subprocess.check_output(['git', 'ls-files'], text=True).splitlines())
diff = tracked - inv
print(f'Total omitidos: {len(diff)}')
"
```
**Resultado observado:**
Los 91 archivos omitidos se dividen en 4 grupos bien definidos:
1. **78 archivos Java** ubicados bajo `backend/src/.../out/...` (omitidos por `"out"` en `EXCLUDED_DIRS`).
2. **1 archivo Java de dominio** `backend/src/main/java/com/riwi/skillbridge/domain/exception/InvalidCredentialsException.java` (omitido por subcadena `"credential"` en `is_sensitive()`).
3. **2 archivos `.env.example`** (`.env.example` y `audit-service/.env.example`) (omitidos por regla `name.startswith(".env")` en `is_sensitive()`).
4. **10 artefactos de compilación rastreados en Git** bajo `audit-service/backend/target/` (omitidos legítimamente por la regla `"target"` de `EXCLUDED_DIRS`, pero revelando un problema de higiene en Git).

---

### 4.3. Contrastación directa de archivos bajo `port/out` y `adapter/out`

**Comando ejecutado:**
```bash
git ls-files | grep -E "(adapter|port)/out" | wc -l
grep -E "(adapter|port)/out" docs/audit/generated/files.csv | wc -l
```
**Resultado observado:**
- Archivos rastreados por Git bajo `port/out` o `adapter/out`: **78**
- Archivos presentes en `docs/audit/generated/files.csv`: **0**

**Distribución de los 78 archivos omitidos:**
- **Puertos de salida y contratos (`backend/src/main/java/com/riwi/skillbridge/application/port/out/`): 32 archivos**
  - Contratos de puertos (24 archivos):
    - `AiRecommendationPort.java`
    - `AiStructuredResponse.java`
    - `BookingEventPublisherPort.java`
    - `BookingRepositoryPort.java`
    - `BookingStatusHistoryPort.java`
    - `CategoryRepositoryPort.java`
    - `NotificationMessage.java`
    - `NotificationPublisherPort.java`
    - `NotificationSenderPort.java`
    - `NotificationType.java`
    - `OfferingCachePort.java`
    - `OfferingRepositoryPort.java`
    - `PasswordHasherPort.java`
    - `PaymentEventPublisherPort.java`
    - `PaymentPort.java`
    - `PaymentResultRepositoryPort.java`
    - `PermanentNotificationException.java`
    - `SpeechToTextPort.java`
    - `TextToSpeechPort.java`
    - `TokenPort.java`
    - `UserAccountPort.java`
    - `UserAdminPort.java`
    - `UserRepositoryPort.java`
    - `VisionPort.java`
  - Eventos de integración (8 archivos en `port/out/event/`):
    - `AuditEventPublisherPort.java`
    - `BookingCancelledPayload.java`
    - `BookingCreatedPayload.java`
    - `BusinessEvent.java`
    - `PaymentApprovedPayload.java`
    - `PaymentRejectedPayload.java`
    - `RecommendationGeneratedPayload.java`
    - `RecommendationRequestedPayload.java`

- **Adaptadores de salida (`backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/`): 35 archivos**
  - **IA (7 archivos):** `GeminiAiAdapter.java`, `GeminiSpeechToTextAdapter.java`, `GeminiVisionAdapter.java`, `GroqSpeechToTextAdapter.java`, `OpenAiAiAdapter.java`, `ElevenLabsTextToSpeechAdapter.java`, `NoOpTextToSpeechAdapter.java` (todos marcados con `@Component`).
  - **Caché Redis (1 archivo):** `RedisOfferingCacheAdapter.java` (marcado con `@Component`, inyecta `StringRedisTemplate`).
  - **Mensajería (4 archivos):** `KafkaAuditEventPublisher.java`, `KafkaBookingEventPublisher.java` (ambos `@Component`, usan `KafkaTemplate`), `RabbitNotificationPublisher.java`, `RabbitPaymentEventPublisher.java` (ambos `@Component`, usan `RabbitTemplate`).
  - **Notificaciones (2 archivos):** `EmailNotificationSender.java`, `SimulatedNotificationSender.java` (ambos `@Component`).
  - **Pagos / Stripe / OpenFeign (4 archivos):**
    - `StripeFeignClient.java` (anotado con `@FeignClient(name = "stripeClient", url = "${app.stripe.url:https://api.stripe.com}")`).
    - `StripeFeignConfig.java` (interceptores y configuración Feign).
    - `StripePaymentAdapter.java` (`@Component("stripePaymentAdapter")`, inyecta `StripeFeignClient`).
    - `StripePaymentIntentResponse.java`.
  - **Pagos simulados y memoria (2 archivos):** `SimulatedPaymentAdapter.java`, `InMemoryPaymentResultRepositoryAdapter.java`.
  - **Persistencia PostgreSQL / JPA (15 archivos):**
    - Entidades (`persistence/entity/`): `BookingEntity.java`, `BookingStatusHistoryEntity.java`, `CategoryEntity.java`, `OfferingEntity.java`, `UserEntity.java` (todos con `@Entity`, `@Table`).
    - Repositorios Spring Data JPA (`persistence/repository/`): `JpaBookingRepository.java`, `JpaBookingStatusHistoryRepository.java`, `JpaCategoryRepository.java`, `JpaOfferingRepository.java`, `JpaUserRepository.java` (todos extienden `JpaRepository`).
    - Adaptadores (`persistence/`): `BookingPersistenceAdapter.java`, `BookingStatusHistoryPersistenceAdapter.java`, `CategoryPersistenceAdapter.java`, `OfferingPersistenceAdapter.java`, `UserPersistenceAdapter.java` (todos con `@Component`).

- **Pruebas automatizadas bajo `port/out` y `adapter/out` (`backend/src/test/java/com/riwi/skillbridge/...`): 11 archivos**
  - `application/port/out/NotificationMessageTest.java`
  - `infrastructure/adapter/out/ai/GeminiAiAdapterTest.java`
  - `infrastructure/adapter/out/ai/GroqSpeechToTextAdapterTest.java`
  - `infrastructure/adapter/out/ai/MockAiAdapter.java`
  - `infrastructure/adapter/out/ai/OpenAiAiAdapterTest.java`
  - `infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java`
  - `infrastructure/adapter/out/notification/EmailNotificationSenderTest.java`
  - `infrastructure/adapter/out/payment/SimulatedPaymentAdapterTest.java`
  - `infrastructure/adapter/out/payment/stripe/StripePaymentAdapterTest.java`
  - `infrastructure/adapter/out/persistence/BookingPersistenceAdapterTest.java`
  - `infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java`

---

### 4.4. Verificación de implementaciones reales de tecnologías cuestionadas

#### 4.4.1. MongoDB
- **Ubicación en código:** `audit-service/backend/`
- **Dependencia:** [audit-service/backend/pom.xml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/pom.xml#L27-L30) incluye `spring-boot-starter-data-mongodb`.
- **Implementación real:**
  - [AuditEventRepository.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/repository/AuditEventRepository.java#L11): `public interface AuditEventRepository extends MongoRepository<BusinessEvent, UUID>`
  - [BusinessEvent.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/model/BusinessEvent.java#L21): `@Document(collection = "audit_events")`
  - [AuditMongoInitializer.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java#L28): Inicializador con `MongoTemplate` que crea índices y colecciones.
- **Pruebas reales:** `AuditEventRepositoryTest.java` ejecuta pruebas con Testcontainers MongoDB (`tc.mongo:7`).
- **Diagnóstico:** MongoDB **SÍ está implementado**. La aparente omisión en `01-java-index.md` se debe a que `JAVA_PATTERNS["Persistence / transactions"]` en `scripts/repo_audit.py` no incluye `MongoRepository` ni `@Document`.

#### 4.4.2. Redis
- **Ubicación en código:** `backend/`
- **Dependencia:** [backend/pom.xml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/pom.xml#L69-L72) incluye `spring-boot-starter-data-redis`.
- **Configuración:** [backend/src/main/resources/application.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/resources/application.yml#L20-L21) (`spring.data.redis.url: ${REDIS_URL:redis://localhost:6379}`).
- **Implementación real:**
  - [RedisOfferingCacheAdapter.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java#L15-L28): Clase anotada con `@Component` que implementa `OfferingCachePort` usando `StringRedisTemplate` para patrón Cache-Aside con TTL configurable.
- **Diagnóstico:** Redis **SÍ está implementado**. Fue completamente omitido del inventario porque se encuentra dentro de `adapter/out/cache/` (podado por `EXCLUDED_DIRS`).

#### 4.4.3. OpenFeign
- **Ubicación en código:** `backend/`
- **Dependencia:** [backend/pom.xml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/pom.xml#L90-L94) incluye `spring-cloud-starter-openfeign` 4.2.0.
- **Habilitación:** [SkillBridgeApplication.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/SkillBridgeApplication.java#L11) `@EnableFeignClients`.
- **Implementación real:**
  - [StripeFeignClient.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignClient.java#L12-L22): Interfaz declarativa `@FeignClient(name = "stripeClient", url = "${app.stripe.url:https://api.stripe.com}", configuration = StripeFeignConfig.class)`.
  - [StripeFeignConfig.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignConfig.java#L10-L16): Configuración con `RequestInterceptor` que inyecta `Authorization: Bearer <STRIPE_SECRET_KEY>`.
  - [StripePaymentAdapter.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapter.java#L22): Inyecta y consume `StripeFeignClient`.
  - [StripePaymentAdapterTest.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapterTest.java): Pruebas unitarias completas de la integración.
- **Diagnóstico:** OpenFeign **SÍ está implementado**. Fue omitido por ubicarse bajo `adapter/out/` y por falta del patrón `@FeignClient` en el script.

#### 4.4.4. Mensajería (RabbitMQ y Apache Kafka)
- **Ubicación en código:** `backend/` y `audit-service/backend/`
- **Dependencias:** `spring-boot-starter-amqp` y `spring-kafka` en los `pom.xml`.
- **Implementación real de consumidores (Inbound):**
  - [KafkaAuditConsumer.java (audit-service)](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java#L15): `@KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}")`
  - [KafkaAuditConsumer.java (backend)](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaAuditConsumer.java#L16): `@KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}")`
  - [NotificationConsumer.java (backend)](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationConsumer.java#L18): `@RabbitListener(queues = RabbitConfiguration.NOTIFICATION_QUEUE)`
  - [PaymentResultConsumer.java (backend)](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java#L21): `@RabbitListener(bindings = @QueueBinding(...))`
- **Implementación real de publicadores (Outbound):**
  - [KafkaAuditEventPublisher.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java#L13-L17): `@Component`, usa `KafkaTemplate<String, BusinessEvent<?>>`.
  - [KafkaBookingEventPublisher.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java#L13-L17): `@Component`, usa `KafkaTemplate<String, BusinessEvent<?>>`.
  - [RabbitNotificationPublisher.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java#L14-L21): `@Component`, usa `RabbitTemplate`.
  - [RabbitPaymentEventPublisher.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java#L9-L17): `@Component`, usa `RabbitTemplate`.
- **Diagnóstico:** Tanto RabbitMQ como Kafka **SÍ están implementados** en capas de entrada y salida. Los publicadores fueron excluidos por residir en `adapter/out/messaging/`.

---

## 5. Diferenciación Rigurosa: Defecto de la Herramienta vs. Estado del Producto

| Elemento / Capacidad | Estado en `scripts/repo_audit.py` (Herramienta) | Estado en la Aplicación (Código de Producto) | Categoría del Hallazgo |
|---|---|---|---|
| **Estructura hexagonal `adapter/out` y `port/out`** | **Defecto confirmado:** La regla `"out"` en `EXCLUDED_DIRS` poda 78 archivos de código Java legítimos. | **Correcto:** La arquitectura hexagonal está adecuadamente implementada siguiendo puertos y adaptadores. | Defecto de la herramienta de auditoría. |
| **Persistencia JPA (PostgreSQL)** | **Defecto de reporte:** Solo 3 coincidencias reportadas en el índice; todas las entidades y repositorios JPA en `adapter/out/persistence` fueron omitidos. | **Correcto:** 5 entidades JPA, 5 repositorios Spring Data JPA y 5 adaptadores implementados y probados. | Defecto de la herramienta de auditoría. |
| **Persistencia MongoDB** | **Defecto de reporte:** `JAVA_PATTERNS` no contempla `MongoRepository` ni `@Document`. | **Correcto:** Servicio `audit-service` con `MongoRepository`, `MongoTemplate`, entidades documentales e IT con Testcontainers. | Defecto de la herramienta de auditoría. |
| **Caché Redis** | **Defecto de reporte:** Archivo podado por la regla `"out"` y sin patrón heurístico para `RedisTemplate`. | **Correcto:** `RedisOfferingCacheAdapter` implementa Cache-Aside con Spring Data Redis y TTL. | Defecto de la herramienta de auditoría. |
| **Integración OpenFeign (Stripe)** | **Defecto de reporte:** Archivos podados por `"out"` y sin patrón para `@FeignClient`. | **Correcto:** `StripeFeignClient` y `StripePaymentAdapter` implementados con pruebas unitarias. | Defecto de la herramienta de auditoría. |
| **Publicación RabbitMQ y Kafka** | **Defecto de reporte:** Publicadores podados por `"out"`; sin patrones para templates ni listeners. | **Correcto:** 4 publicadores (`KafkaTemplate`, `RabbitTemplate`) y 4 consumidores (`@KafkaListener`, `@RabbitListener`). | Defecto de la herramienta de auditoría. |
| **Excepción de dominio de credenciales** | **Defecto confirmado:** `InvalidCredentialsException.java` omitido por falso positivo de sensibilidad en `is_sensitive()`. | **Correcto:** Excepción de dominio válida para manejo de autenticación. | Defecto de la herramienta de auditoría. |
| **Archivos compilados en `audit-service/.../target/`** | **Correcto en el script:** `EXCLUDED_DIRS` ignora la carpeta `target`. | **Defecto de repositorio/higiene:** Existen 10 artefactos compilados (incluyendo `.jar`) rastreados por Git en el historial. | Defecto de repositorio / Git hygiene. |

---

## 6. Limitaciones

1. **Sin ejecución de servicios externos:** Esta auditoría se realizó exclusivamente mediante análisis estático y de lectura de código fuente y metadatos de Git. No se levantaron contenedores Docker ni se enviaron peticiones a brokers o bases de datos activas.
2. **Sin compilación ni ejecución de tests de producto:** No se ejecutó `./mvnw test` ni `npm test` para no alterar el entorno de trabajo ni generar artefactos temporales adicionales en esta fase.
3. **No se modificó el código:** Siguiendo las directrices del procedimiento, ni `scripts/repo_audit.py` ni ningún archivo de producto fue editado o corregido en esta fase.

---

## 7. Conclusión

Queda **CONFIRMADO** que la presunta ausencia de persistencia, Redis, MongoDB, OpenFeign y mensajería en los informes de auditoría iniciales fue un **falso negativo masivo provocado por dos defectos en `scripts/repo_audit.py`**:
1. La coincidencia directa `"out"` en `EXCLUDED_DIRS` que podó 78 archivos de puertos, adaptadores y pruebas.
2. La falta de patrones para Spring Data MongoDB, Redis, OpenFeign y mensajería en `JAVA_PATTERNS`.
3. El filtro `is_sensitive()` que confundió `InvalidCredentialsException.java` con un secreto.

El código de producto **sí posee las implementaciones correspondientes** y respeta la arquitectura hexagonal declarada. Adicionalmente, se detectó un problema de higiene de repositorio donde 10 artefactos de compilación bajo `audit-service/backend/target/` fueron indebidamente versionados en Git.
