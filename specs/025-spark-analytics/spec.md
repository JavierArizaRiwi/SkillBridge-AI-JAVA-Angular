# Feature Specification: Batch Analytics with Apache Spark
**Feature Branch**: `feature/025-spark-analytics` | **Created**: 2026-10-22 | **Status**: Draft (refinada 2026-11-09; pendiente de aprobación humana) | **Issue**: #76
**Input**: Como Admin / analista de negocio, quiero que las métricas analíticas históricas (reservas, cancelaciones, conversión de recomendaciones y demanda por oferta/día) se calculen con un pipeline batch en Apache Spark sobre los eventos de negocio, para poder escalar el análisis a grandes volúmenes sin cargar el camino transaccional.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Procesamiento Batch Idempotente de Métricas Diarias por Oferta (Priority: P1)

Como analista de negocio o administrador de la plataforma, quiero ejecutar un job batch sobre lotes históricos de eventos de negocio para calcular con exactitud agregados diarios por oferta (reservas, cancelaciones, recomendaciones y tasa de conversión efectiva), asegurando que el resultado sea matemáticamente determinista e idéntico a una implementación de referencia sin sobrecargar la base de datos OLTP.

- **Why this priority**: Constituye el valor central de negocio y el MVP funcional por sí solo. Si el cálculo batch no es idéntico a la referencia o no es idempotente ante eventos duplicados, ninguna capacidad analítica posterior es confiable.
- **Independent Test**: Ejecutar el pipeline de procesamiento batch sobre un lote de eventos de prueba (dataset controlado / golden dataset) con ejecuciones repetidas y comparar campo a campo la salida de métricas agregadas contra una implementación de referencia (SQL determinista / Python). La salida debe coincidir en el 100% de los registros y mantener idéntico resultado ante re-ejecución con eventos duplicados.
- **Acceptance Scenarios**:
  - **Scenario 1.1 (Cálculo exacto de agregados diarios)**:
    - *Given*: Un lote de eventos para la oferta `O-101` y la fecha `2026-10-20` (UTC) que contiene 200 eventos `RecommendationGenerated` que incluyen `O-101`, 100 eventos `BookingCreated` (50 con `recommendationId`) y 20 eventos `BookingCancelled` (10 con `recommendationId`).
    - *When*: Se ejecuta el pipeline analítico batch sobre dicho lote.
    - *Then*: El sistema produce un registro diario para `(O-101, 2026-10-20)` con `recommendations = 200`, `bookings_total = 100`, `cancellations_total = 20`, `bookings_attributed = 50`, `cancellations_attributed = 10`, `effective_attributed = 40`, `conversion_rate = 0.20` (40 / 200) y `total_booking_rate = 0.40` (80 / 200). Definiciones normativas en `docs/contracts/analytics-events.md` §3.
  - **Scenario 1.2 (Idempotencia ante reintentos y eventos duplicados)**:
    - *Given*: Un lote de eventos donde un 25% de los registros tienen identificadores `eventId` duplicados (simulando reintentos de entrega de mensajería).
    - *When*: Se ejecuta el pipeline analítico batch.
    - *Then*: Los eventos duplicados por `eventId` se deduplican en el paso inicial de ingesta y las métricas finales agregadas son exactamente iguales a las generadas con el dataset sin duplicar.
  - **Scenario 1.3 (Manejo de lote vacío y ausencia de recomendaciones)**:
    - *Given*: Un lote sin eventos para una fecha determinada, o un conjunto de eventos de reserva para una oferta con 0 recomendaciones previas.
    - *When*: Se procesa el lote mediante el pipeline batch.
    - *Then*: Para el lote sin eventos se genera una salida vacía sin errores; y para la oferta con 0 recomendaciones, el sistema calcula `conversionRate = 0.0` sin generar excepciones por división por cero.

---

### User Story 2 - Aislamiento Arquitectónico y Almacenamiento Analítico Desacoplado (Priority: P2)

Como ingeniero de software / plataforma, quiero que el pipeline batch de Spark esté alojado en un módulo completamente aislado del repositorio y deposite sus resultados en un destino de datos puramente analítico (ficheros Parquet o tabla analítica segregada), para garantizar que el backend transaccional (`backend/`), el frontend y el servicio de auditoría no se vean afectados por dependencias pesadas, consumo de memoria o escrituras en el modelo OLTP.

- **Why this priority**: Evita la degradación del rendimiento de la base de datos operacional y el acoplamiento de dependencias entre el motor Big Data y la API REST transaccional de Spring Boot.
- **Independent Test**: Compilar y ejecutar el módulo analítico verificando mediante inspección de dependencias que ningún archivo `pom.xml` o `package.json` de los otros módulos (`backend/`, `frontend/`, `audit-service/`) fue modificado, y verificar mediante auditoría de base de datos que ninguna tabla OLTP (`bookings`, `offerings`, `users`) recibió escrituras ni bloqueos durante la ejecución del job.
- **Acceptance Scenarios**:
  - **Scenario 2.1 (Escritura exclusiva en destino analítico)**:
    - *Given*: Un entorno de ejecución donde la base de datos OLTP contiene tablas transaccionales activas y se define un destino analítico desacoplado (directorio de salida Parquet o esquema analítico separado).
    - *When*: El job de analítica batch procesa un lote de eventos históricos.
    - *Then*: Todos los resultados agregados se escriben únicamente en el destino analítico configurado, sin realizar mutaciones (INSERT, UPDATE o DELETE) en las tablas transaccionales del backend.
  - **Scenario 2.2 (Aislamiento de empaquetado y dependencias fijas)**:
    - *Given*: El módulo de analítica batch ubicado en su directorio independiente (ej. `analytics/spark/`).
    - *When*: Se compila y empaqueta el artefacto del job batch.
    - *Then*: La compilación utiliza dependencias con versiones explícitas y fijadas (pinned versions) y no añade artefactos ni dependencias de Spark/Hadoop al `pom.xml` de `backend/`.

---

### User Story 3 - Operación Contenerizada Opcional y Gobernanza de Umbrales (Priority: P3)

Como operador de infraestructura / DevOps, quiero que el servicio de analítica batch pueda desplegarse opcionalmente mediante Docker Compose bajo un perfil dedicado (`analytics`) desactivado por defecto, con límites de memoria configurables y documentación operativa que estipule umbrales cuantitativos de activación, para no malgastar recursos en entornos livianos o con poco volumen de eventos.

- **Why this priority**: Permite estandarizar la ejecución reproducible tanto en modo local standalone como en contenedores, evitando el consumo innecesario de CPU y memoria en desarrolladores locales o despliegues con volúmenes donde soluciones más simples (ej. DuckDB o queries SQL) son más eficientes.
- **Independent Test**: Ejecutar `docker compose up` estándar y confirmar que el servicio analítico permanece inactivo. Posteriormente, invocar `docker compose --profile analytics up` con variables de límite de memoria definidas (ej. 1 GB de driver) y validar que el servicio levanta respetando los límites asignados y ejecuta el pipeline de forma reproducible según las instrucciones de su README.
- **Acceptance Scenarios**:
  - **Scenario 3.1 (Servicio inactivo por defecto)**:
    - *Given*: El descriptor de orquestación local `docker-compose.yml` que incluye el servicio analítico bajo el perfil `analytics`.
    - *When*: Un desarrollador levanta la plataforma con `docker compose up -d` (sin especificar perfiles).
    - *Then*: Los servicios transaccionales levantan normalmente y ningún contenedor de Spark o job analítico batch se inicia.
  - **Scenario 3.2 (Activación bajo perfil con límites de recursos)**:
    - *Given*: El entorno local donde se define el perfil `--profile analytics` y variables de memoria configuradas.
    - *When*: Se ejecuta `docker compose --profile analytics up`.
    - *Then*: El servicio analítico inicia en un contenedor aislado, aplica los límites de memoria definidos y completa la ejecución del procesamiento por lotes según el comando reproducible documentado.
  - **Scenario 3.3 (Criterios cuantitativos de adopción documentados)**:
    - *Given*: La documentación técnica (README) del componente analítico.
    - *When*: Un ingeniero evalúa activar el pipeline batch en producción.
    - *Then*: El documento provee umbrales cuantitativos explícitos (ej. >500.000 eventos/día o >100 GB de historial acumulado) justificando cuándo la sobrecarga de arranque de Spark amortiza frente a alternativas ligeras.

---

### Edge Cases

1. **Eventos con timestamps en bordes de fecha (Medianoche UTC)**:
   - Eventos generados a las `23:59:59.999Z` y `00:00:00.001Z` deben agregarse de forma estricta en sus fechas correspondientes basadas en zona horaria UTC (`occurredAt`), evitando desfases por husos horarios locales.
2. **Duplicados exactos vs Duplicados con payload divergente**:
   - Si dos eventos comparten el mismo `eventId` pero presentan discrepancias en otros campos debido a inconsistencias de red, el pipeline debe conservar deterministamente la primera instancia encontrada o descartar la duplicación sin interrumpir el lote.
3. **Eventos sin oferta asociada (`offeringId` nulo o faltante)**:
   - En eventos de recomendaciones globales o de intención de usuario donde `offeringId` no está asignado en el momento de generación, el pipeline debe ignorarlos o agregarlos a un bucket global sin corromper las métricas agrupadas por oferta específica.
4. **Tasa de cancelación anómala (más cancelaciones que reservas en el día)**:
   - En días donde las cancelaciones de reservas agendadas en fechas anteriores superen a las nuevas reservas creadas, el cálculo de reservas efectivas no debe ser negativo: `effectiveBookings = Math.max(0, bookings - cancellations)`.
5. **Conversión superior al 100% (Reservas directas sin recomendación)**:
   - Si una oferta recibe reservas directas en la plataforma sin haber sido expuesta en recomendaciones previas (`recommendations = 0`), la tasa de conversión debe computarse como `0.0` (o limitarse a `1.0` si se maneja tasa acotada), evitando divisiones por cero o valores infinitos.
6. **Lote de datos completamente vacío (Zero-byte o 0 eventos)**:
   - La ingesta de un lote vacío no debe provocar excepciones ni fallos de ejecución; debe finalizar con código de salida exitoso y emitir 0 registros de salida.
7. **Desincronización de reloj / Timestamps futuros (decidido: cuarentena)**:
   - Eventos con `occurredAt` posterior al instante de corte del lote se apartan a `quarantine/` sin abortar el proceso, y se informa su número (`docs/contracts/analytics-events.md` §5).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE ingerir lotes de eventos de negocio persistentes estructurados bajo el esquema común `BusinessEvent` provenientes de exportaciones del topic Kafka `booking-events` (y `recommendation-events`) o volcados estructurados (JSON, JSON Lines o Parquet).
- **FR-002**: El sistema DEBE registrar y etiquetar el origen de los datasets de entrada, clasificando obligatoriamente cualquier fuente de datos sintéticos o simulados con la etiqueta `synthetic` para distinguirlos de datos de producción (`production`).
- **FR-003**: El sistema DEBE calcular para cada combinación única de oferta (`offeringId`) y fecha (`date` en UTC) las siguientes métricas agregadas:
  - Total de reservas creadas en el día (`bookings`).
  - Total de reservas canceladas en el día (`cancellations`).
  - Total de reservas efectivas (`effectiveBookings = max(0, bookings - cancellations)`).
  - Total de veces que la oferta fue recomendada por IA (`recommendations`).
  - Total de usuarios únicos impactados por recomendaciones de la oferta (`unique_users`).
  - Métricas de atribución y conversión según `docs/contracts/analytics-events.md` §3: `bookings_total`, `cancellations_total`, `bookings_attributed`, `cancellations_attributed`, `effective_attributed`, la **conversión oficial** `conversion_rate = effective_attributed / recommendations` (o `0.0` si no hay recomendaciones) y la tasa informativa `total_booking_rate`. Los eventos v1 sin `recommendationId` cuentan solo en los totales.
- **FR-004**: El sistema DEBE garantizar idempotencia estricta en el procesamiento batch deduplicando los eventos por su identificador único `eventId` antes de ejecutar cualquier cálculo de agregación.
- **FR-005**: El sistema DEBE persistir las métricas calculadas exclusivamente en un destino de almacenamiento analítico desacoplado (directorio de ficheros Parquet estructurados o tabla analítica segregada en base de datos sin relaciones de integridad referencial hacia el esquema OLTP), prohibiendo cualquier operación de escritura en las tablas transaccionales del backend (`bookings`, `offerings`, `users`, `recommendation_events`, `recommendation_daily_metrics` transaccional).
- **FR-006**: El job batch DEBE soportar ejecución en modo local (`local[*]`) para entornos de desarrollo y pruebas automatizadas, así como modo empaquetado para despliegue distribuido vía gestor de recursos de Spark (`spark-submit`).
- **FR-007**: El sistema DEBE incluir un arnés de pruebas automatizadas que compare los resultados numéricos de las agregaciones de Spark contra una implementación de referencia (SQL determinista o script en Python), certificando una coincidencia matemática del 100% en los resultados sobre datasets de prueba compartidos.
- **FR-008**: El sistema DEBE estructurarse dentro de un módulo independiente del repositorio (ej. `analytics/spark/`) con su propio archivo de definición de proyecto y dependencias con versiones fijadas (pinned versions).
- **FR-009**: El sistema NO DEBE modificar los archivos de dependencias ni de código de los módulos transaccionales existentes (`backend/`, `frontend/`, `audit-service/`, `backend/pom.xml`, `frontend/package.json`, `audit-service/backend/pom.xml`).
- **FR-010**: El sistema DEBE proveer una definición de servicio opcional en `docker-compose.yml` encapsulada bajo el perfil `analytics` (desactivada por defecto al ejecutar `docker compose up`), con variables de entorno para parametrizar los límites de memoria de ejecución (driver y workers).
- **FR-011**: El sistema DEBE incluir un archivo `README.md` en su módulo que documente de forma reproducible: requisitos de sistema (versiones de JDK, Apache Spark y memoria mínima), comandos de compilación y ejecución local/dockerizada, y criterios cuantitativos objetivos (umbrales de adopción) para justificar la activación del job en producción frente a alternativas más ligeras.
- **FR-012**: El sistema DEBE ejecutar la totalidad de su pipeline analítico y suite de pruebas sin requerir credenciales de proveedores externos ni servicios de pago (OpenAI, Gemini, Stripe, Mailgun o servicios cloud pagos).

---

### Key Entities

- **BusinessEventBatch**: Colección inmutable de eventos de negocio ingeridos en el lote. Cada evento contiene:
  - `eventId` (UUID): Identificador único universal del evento utilizado como clave de deduplicación e idempotencia.
  - `eventType` (String): Tipo de evento (`BookingCreated`, `BookingCancelled`, `RecommendationGenerated`).
  - `aggregateId` (String): Identificador de la entidad de negocio principal.
  - `aggregateType` (String): Tipo de agregado (`Booking`, `Offering`, `Recommendation`).
  - `occurredAt` (Timestamp UTC): Momento de ocurrencia del evento.
  - `payload` (Map/Struct): Datos específicos del evento (`offeringId`, `customerId` / `userId`, etc.).
- **AggregatedDailyMetric**: Registro analítico derivado que representa las métricas calculadas para una oferta en una fecha específica:
  - `offeringId` (UUID/String): Identificador del servicio ofertado.
  - `date` (Date UTC): Día calendario del agregado.
  - `bookings` (Long): Conteo de reservas creadas.
  - `cancellations` (Long): Conteo de cancelaciones registradas.
  - `effectiveBookings` (Long): Saldo neto de reservas (`max(0, bookings - cancellations)`).
  - `recommendations` (Long): Conteo de recomendaciones generadas.
  - `uniqueUsers` (Long): Conteo de usuarios únicos que recibieron la recomendación.
  - `conversionRate` (Double): Proporción de conversión (`effectiveBookings / recommendations`).
- **AnalyticsDatasetMetadata**: Registro descriptivo de auditoría del lote analítico procesado:
  - `batchId` (UUID/String): Identificador de la corrida batch.
  - `sourceType` (String): Tipo de fuente (`kafka-export`, `postgres-dump`).
  - `environmentType` (String): Clasificación del lote (`synthetic` o `production`).
  - `processedAt` (Timestamp UTC): Momento de finalización del cálculo.
  - `totalEventsIngested` (Long): Cantidad total de eventos leídos.
  - `deduplicatedEventsCount` (Long): Cantidad de eventos únicos procesados tras deduplicación.
  - `timeWindow` (DateRange): Rango de fechas cubierto por el lote.
- **QuarantineEventRecord**: Entidad que almacena eventos anómalos o descartados:
  - `eventId` (UUID/String): Identificador del evento anómalo.
  - `rawEvent` (String/Binary): Contenido original del evento.
  - `rejectionReason` (String): Motivo del rechazo (ej. `MALFORMED_PAYLOAD`, `INVALID_TIMESTAMP`, `UNRECOGNIZED_SCHEMA`).
  - `detectedAt` (Timestamp UTC): Momento de detección.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 (Paridad exacta con referencia)**: El 100% de las métricas agregadas calculadas por el pipeline batch en Spark (`bookings`, `cancellations`, `effectiveBookings`, `conversionRate`) coinciden exactamente (tolerancia numérica de error $\le 10^{-4}$ en decimales de tasa) con los resultados de la implementación de referencia en pruebas automatizadas sobre datasets de validación.
- **SC-002 (Invarianza ante duplicados)**: La inyección de un 30% de eventos duplicados artificiales en el dataset de entrada produce una salida idéntica (0% de variación en todos los agregados) comparada con la ejecución sobre el dataset deduplicado.
- **SC-003 (Aislamiento de módulos y dependencias)**: 0 archivos modificados en `backend/`, `frontend/`, `audit-service/` y 0 dependencias de Apache Spark o Hadoop agregadas a los archivos de construcción de dichos módulos.
- **SC-004 (Cero acoplamiento transaccional)**: 0 operaciones de modificación (INSERT, UPDATE, DELETE o ALTER) ejecutadas sobre tablas del esquema relacional transaccional (OLTP) de la plataforma.
- **SC-005 (Independencia de credenciales y costos)**: El 100% de la suite de pruebas del módulo analítico se ejecuta en local y en entornos de CI sin requerir conexión a internet, credenciales externas ni servicios en la nube de pago.
- **SC-006 (Gobernanza y umbrales operacionales cuantitativos)**: El tiempo de ejecución del job batch sobre un dataset de prueba de $10^6$ eventos en un entorno de pruebas estándar se completa en menos de 10 segundos, y la documentación define con precisión cuantitativa el umbral de adopción en producción (volumen $\ge 500.000$ eventos/día o almacenamiento histórico $\ge 100$ GB) justificando la activación del perfil `analytics`.

---

## Assumptions

### Decisiones y supuestos que requieren clarificación de producto / negocio

- **[DECIDIDO 2026-11-09: Destino analítico primario → Opción A, Parquet particionado por fecha]**: El criterio de aceptación del issue establece *"Destino de resultados definido (tabla analítica separada o ficheros Parquet) sin escribir en tablas OLTP"*. Se debe decidir cuál será el formato primario oficial para el consumo de analítica:
  - *Opción A (Recomendada)*: Ficheros Parquet estructurados y particionados por fecha (`date=YYYY-MM-DD/`) almacenados en directorio/volumen analítico desacoplado (formato columnar estándar para analítica y Big Data).
  - *Opción B*: Tabla analítica segregada en la base de datos PostgreSQL (ej. esquema `analytics.daily_metrics`) sin claves foráneas a las tablas OLTP.
  - *Opción C*: Formato dual: generación nativa en Parquet y comando opcional de sincronización a tabla relacional analítica si herramientas de reportería SQL lo requieren.
- **[DECIDIDO 2026-11-09: Alcance → Opción A, solo agregados diarios; `trend_score` y pronóstico quedan en HU-22]**: El criterio del issue solicita *"Calcula al menos recommendation_daily_metrics (reservas, cancelaciones, conversión por oferta y día)"*. En el backend actual, `DemandAnalyticsService` calcula `trend_score` (fórmula con ventana móvil de 14 días) y `forecast_next_7_days`. Se requiere acordar el alcance del cálculo en el job batch:
  - *Opción A (Recomendada)*: Limitar el MVP batch de esta especificación a las métricas agregadas directas por oferta y día (`recommendations`, `unique_users`, `bookings`, `cancellations`, `effectiveBookings`, `conversion_rate`), dejando `trend_score` y `forecast_next_7_days` como una extensión o vista analítica posterior.
  - *Opción B*: Replicar el algoritmo completo de ventana deslizante de 14 días (`growthRate`, `trendScore`, `forecastNext7Days`) dentro del job Spark, requiriendo que cada lote de entrada garantice al menos 14 días continuos de datos históricos.
- **[DECIDIDO 2026-11-09: Eventos con timestamps futuros → Opción A, cuarentena sin abortar]**: Cómo actuar ante eventos con timestamps de ocurrencia posteriores a la fecha de corte del procesamiento por lotes:
  - *Opción A (Recomendada)*: Segregar los eventos anómalos (`occurredAt > execution_timestamp`) en un dataset de cuarentena (`quarantine/`) sin abortar el job principal.
  - *Opción B*: Descartar silenciosamente los eventos anómalos emitiendo una alerta en el registro de métricas de calidad de datos.
  - *Opción C*: Tolerar un desfase máximo (ej. hasta 10 minutos por desincronización de reloj de nodo emisor) ajustando el evento a la ventana actual y cuarentenar desviaciones mayores.

### Hechos verificados en el código y configuración del repositorio (`[VERIFIED]`)

- `[VERIFIED]` `backend/src/main/resources/db/migration/V11__recommendation_events.sql#L6-L16`: La tabla `recommendation_events` define el esquema de eventos de recomendación con clave primaria `id UUID` que mapea a `BusinessEvent.eventId` para garantizar idempotencia, registrando `recommendation_id`, `user_id`, `offering_id` (nullable), `event_type`, `occurred_at`.
- `[VERIFIED]` `backend/src/main/resources/db/migration/V12__recommendation_daily_metrics.sql#L6-L25`: La tabla `recommendation_daily_metrics` contiene `(id, date, offering_id, recommendations, unique_users, bookings, cancellations, conversion_rate, trend_score, forecast_next_7_days)` con restricción de unicidad sobre `(offering_id, date)`.
- `[VERIFIED]` `backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BusinessEvent.java#L13-L31`: El envelope común de eventos contiene `eventId` (UUID), `eventType` (String), `aggregateId`, `aggregateType`, `occurredAt` (Instant), `correlationId`, `version`, `payload` y metadatos de actor y acción.
- `[VERIFIED]` `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaAnalyticsConsumer.java#L23-L38`: La analítica incremental actual escucha `booking-events` y `recommendation-events` en el grupo `skillbridge-analytics-group`, procesando `RecommendationGenerated`, `BookingCreated`, `BookingCancelled` de forma idempotente mediante verificación previa de `eventId`.
- `[VERIFIED]` `backend/src/main/java/com/riwi/skillbridge/application/service/DemandAnalyticsService.java#L26-L54`: Las fórmulas de negocio implementadas definen `conversionRate = effectiveBookings / recommendations` (donde `effectiveBookings = Math.max(0, bookings - cancellations)`), `growthRate`, `trendScore` compuesto de 0 a 100 y `forecastNext7Days`.
- `[VERIFIED]` `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/RecommendationAnalyticsPersistenceAdapter.java#L104-L108`: La persistencia de métricas recalcula `conversionRate` usando `Math.max(0L, bookings - cancellations) / recommendations` si `recommendations > 0`, y `0.0` en caso contrario.
- `[VERIFIED]` `docs/adr/0003-kafka-event-topic-unification.md#L21-L35`: Los eventos de negocio y de auditoría fueron unificados en el topic `booking-events` bajo la configuración `${app.kafka.topic.booking-events}`.
- `[VERIFIED]` `docker-compose.yml#L137-L149`: Los servicios auxiliares u opcionales (tales como Prometheus y Grafana) utilizan la directiva `profiles: [observability]`, estableciendo el patrón de perfiles a seguir para `profiles: [analytics]`.

### Supuestos deducidos de la arquitectura (`[INFERRED]`)

- `[INFERRED]`: El job Spark debe ser un proyecto completamente autónomo en su propia carpeta (ej. `analytics/spark/`) con build tool propio (Maven/Sbt/Gradle o Poetry/pip si PySpark), sin compartir `pom.xml` padre ni añadir JARs de Spark al classpath de la aplicación Spring Boot.
- `[INFERRED]`: La ingesta de datos del job batch operará sobre volcados periódicos o archivos exportados desde Kafka/PostgreSQL (formatos JSONL o Parquet) en lugar de una conexión directa que bloquee tablas relacionales de producción.
- `[INFERRED]`: Conforme a la evaluación técnica previa SPARK-001 (donde DuckDB procesó $10^6$ eventos en 1,5 s frente a 5,9 s de Spark por el overhead de la JVM), el uso de Spark en SkillBridge no se justifica para la escala actual de demostración, por lo que su inclusión debe ser estrictamente modular, apagada por defecto y orientada a preparación de escala futura.

---

## Relación con HU-22 (decidida el 2026-11-09)

HU-22 y HU-25 calculan métricas parecidas y NO deben duplicarse. Frontera:

| | HU-22 (monolito) | HU-25 (Spark) |
|---|---|---|
| Rol | Servir tendencias **en línea** a la aplicación | **Reprocesar el histórico** fuera del camino transaccional |
| Datos | PostgreSQL: `recommendation_events`, `recommendation_daily_metrics` | Eventos exportados de Kafka (JSON/JSONL/Parquet) |
| Métricas | `trendScore`, `conversionRate`, pronóstico de 7 días | Agregados diarios por oferta: recomendaciones, reservas, cancelaciones, conversión |
| Escribe en | Tablas del monolito | **Solo** almacén analítico aparte; nunca tablas OLTP |
| Ejecución | Incremental, en tiempo real | Batch bajo demanda (perfil `analytics` de Compose) |

HU-22 NO depende de Spark para funcionar. Spark **valida** a HU-22: sobre un dataset compartido, ambos deben producir los mismos agregados (FR-007). Los agregados de HU-25 usan la **definición de conversión que fije HU-22** (ver `specs/022-demand-analytics/spec.md`), no la actual.

## Alcance honesto de Spark

La evaluación previa SPARK-001 (tarea de `agy` del 2026-10-22; informe en el commit `3bc992a`, `docs/tasks/reports/SPARK-001/`, ya no presente en el árbol actual) **reportó**, sobre datos sintéticos y en una sola máquina, DuckDB 1,48 s frente a Spark 5,92 s para 10^6 eventos. El coordinador **no re-ejecutó** ese benchmark (`[REPORTED]`). Su decisión registrada fue **no adoptar Spark** y usar DuckDB si hiciera falta un batch (`DECISIONS-RAG-SPARK.md`). Esta HU lo reintroduce como componente opcional; no hay medición propia del volumen real. **Decisión (2026-11-09): Spark es un requisito obligatorio de esta HU** (lo exige el curso / quien evalúa el proyecto), aunque el volumen actual no lo justifique técnicamente. Por tanto se entrega Spark, pero con un diseño que no oculta eso:

- **Contrato de motor común**: la entrada (eventos `BusinessEvent`) y la salida (agregados diarios por oferta) se definen independientemente del motor.
- **Motor de referencia ligero** (SQL/DuckDB o Python, ya exigido por FR-007) como validador independiente: Spark DEBE producir resultados idénticos a la referencia sobre el mismo dataset.
- **Lenguaje: Python (PySpark), independiente del proyecto Java** (decidido 2026-11-09). El módulo no usa Maven ni comparte `pom.xml`, código ni dependencias con `backend/` o `audit-service/`; se construye con su propio gestor de dependencias de Python con **versiones fijadas** (`==`). Aclaración técnica: Spark en sí corre sobre la JVM, así que PySpark necesita un JDK en tiempo de ejecución. Esa necesidad se encapsula en la imagen de su contenedor (perfil `analytics`); en una máquina sin Docker habría que tener un JDK instalado, pero **no es el JDK ni el build del proyecto Java**. La versión de Python, de PySpark y del JDK compatibles se verifican y fijan en el plan, no en esta spec.
- **Entrada: archivos JSONL exportados** (decidido 2026-11-09). El job solo lee un directorio con `manifest.json` y `events-*.jsonl`; no se conecta a Kafka ni a bases de datos. Formato, procedencia (`production` | `synthetic`) y regla de cuarentena en `docs/contracts/analytics-events.md` §4–5. Los datos de prueba salen de un **generador determinista** con semilla.
- **Definiciones y reglas compartidas**: las métricas (`conversion_rate` atribuida, `bookings_total`, etc.), la deduplicación determinista por `eventId` y la salida idempotente por partición están en `docs/contracts/analytics-events.md` §3 y §5. La referencia en Python y Spark DEBEN dar resultados idénticos.
- **Dependencia con HU-22**: el cálculo sobre datos reales exige `RecommendationGenerated` v2 y `recommendationId` en las reservas (HU-22). Con **datos sintéticos** HU-25 puede desarrollarse y probarse **sin esperar** a HU-22.
- **Spark se ejecuta bajo el perfil `analytics`** de Compose (no arranca por defecto), sin tocar `backend/`, `frontend/` ni `audit-service/` (FR-009).
- El README del módulo DEBE decir con claridad que Spark se adopta por requisito y no por volumen, y listar los umbrales cuantitativos en los que sería técnicamente necesario (FR-011).

Las tres decisiones de producto de la sección Assumptions quedaron **decididas el 2026-11-09**: destino **Parquet** particionado por fecha; alcance **solo agregados diarios** por oferta (la tendencia y el pronóstico siguen en HU-22); eventos con fecha futura a **cuarentena** sin abortar el proceso.

---

## Dependencies & Out of Scope

### Dependencies
- **Formato de Eventos de Negocio**: Dependencia del esquema `BusinessEvent` emitido por el backend en el topic `booking-events` o sus volcados estructurados.
- **Java Development Kit (JDK)**: Compatibilidad con el entorno de ejecución Java fijado para Spark (habitualmente JDK 17 / 21 según versión de Spark).
- **Docker & Docker Compose**: Dependencia de la directiva `profiles` de Docker Compose para la ejecución opcional del contenedor analítico.

### Out of Scope
- **Reemplazar el consumidor Kafka incremental existente**: El componente `KafkaAnalyticsConsumer` de `backend/` continuará operando para la actualización transaccional en tiempo real de `recommendation_daily_metrics`. El job Spark no lo sustituye ni lo altera.
- **Despliegue de clúster gestionado en la nube**: No se implementará infraestructura ni aprovisionamiento de clústeres cloud gestionados (AWS EMR, Google Cloud Dataproc, Azure Databricks) ni gestión de Kubernetes/YARN.
- **Modificación de la interfaz web (Frontend)**: No se alterarán los componentes Angular ni las pantallas de la plataforma para conectar directamente al almacenamiento analítico.
- **Generación de modelos de Machine Learning avanzados**: La HU-25 cubre exclusivamente agregación determinista de métricas históricas de oferta y demanda, no entrenamiento de modelos predictivos o LLMs.
