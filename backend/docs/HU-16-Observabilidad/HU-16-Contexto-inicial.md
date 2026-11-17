### 1. ¿De qué se trata esta Historia de Usuario?

Originalmente, esta historia solo pedía ver si el backend monolítico de Spring Boot estaba vivo y qué latencia tenía. Sin embargo, el proyecto ahora tiene una **arquitectura híbrida distribuida**:
* **Monolito Spring Boot** (puerto `8080`).
* **Audit Service** (`audit-service/backend`, Spring Boot + MongoDB, puerto `8081`).
* **Mentorship Service** (NestJS) y comunicación por **RabbitMQ** y **Kafka**.

**El problema de negocio:**
Si un usuario dice *"Pagué mi mentoría y no me aparece"*, el problema puede haber ocurrido en:
1. La pasarela de pago / Monolito (HTTP).
2. La cola de RabbitMQ (`payment.events` o cayó a `mentorship.payment.dlq`).
3. El servicio de mentorías en NestJS.
4. El evento Kafka hacia el servicio de auditoría (`booking-events`).
5. La inserción en MongoDB de `audit-service`.

**El objetivo de la HU-16:**
Tener una visión centralizada de salud, métricas y trazabilidad de extremo a extremo:
1. **Métricas en tiempo real (Prometheus + Grafana):** Ver si algún servicio está caído, si hay errores HTTP 4xx/5xx, latencias p95, mensajes atorados en colas de RabbitMQ (especialmente DLQs) o retraso (lag) de consumidores Kafka.
2. **Trazabilidad (`X-Correlation-Id`):** Que un identificador único viaje por todo el circuito (HTTP → RabbitMQ → NestJS → Kafka → Audit) para que buscando ese ID en auditoría se pueda reconstruir todo el ciclo de vida del flujo.

---

### 2. ¿Qué tienes que hacer? (Desglose de la solución)

La HU se divide en 4 componentes principales:

#### A. Métricas y Health en los servicios
* **Monolito Spring:** Ya tiene Actuator y Micrometer Prometheus. Debe garantizarse que la métrica lleve la etiqueta `application="skillbridge-backend"`.
* **Audit Service (`audit-service`):** Actualmente solo expone `health,info`. Requiere habilitar el endpoint de Prometheus (`micrometer-registry-prometheus`) y la etiqueta `application="audit-service"`.
* **Mentorship Service (NestJS):** Debe exponer `/metrics` (con la librería `prom-client`) y su health check etiquetado con `application="mentorship-service"`.

#### B. Recolección de métricas (Prometheus)
* En [prometheus.yml](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/ops/prometheus/prometheus.yml) hoy solo está configurado el scrape de `backend:8080`.
* Se deben agregar los targets de los demás componentes activos (`audit-backend:8081`, RabbitMQ, etc.).

#### C. Dashboard unificado en Grafana
* En [ops/grafana/provisioning](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/ops/grafana/provisioning) solo existe el data source de Prometheus, **no hay dashboards aún**.
* Se debe crear un dashboard con paneles para:
  1. Estado de salud (UP/DOWN) por servicio.
  2. Tasa de errores HTTP (4xx / 5xx) por servicio.
  3. Latencia percentil 95 ($p95$).
  4. Mensajes en colas y DLQ de RabbitMQ (ej. `mentorship.payment.dlq`).
  5. Lag de consumidores Kafka.
  6. *(Espacio reservado para las métricas de fallback de IA cuando se implemente HU-28)*.

#### D. Trazabilidad de `X-Correlation-Id`
* Garantizar que cuando entra un request HTTP con `X-Correlation-Id`, este valor se incluya en el payload del evento RabbitMQ (`PaymentApproved`), pase por NestJS y llegue al evento Kafka registrado en `audit-service`.
* Probar una consulta en `audit-service` filtrando por `correlationId` para demostrar que se recupera el rastro completo.

#### E. Seguridad y Evidencias
* Comprobar que en métricas y logs no viajen secretos (API Keys, tokens, passwords).
* Grafana no debe usar credenciales por defecto (`admin/admin`) en producción/variables de entorno.
* Documentar comandos de prueba y capturas del dashboard funcionando.

---

### 3. ¿Qué puedes hacer mientras tanto? (Plan de acción inmediato)

No necesitas esperar a que todos los demás servicios o HUs estén listos para avanzar. La regla explícita es: **"no bloquear por un dashboard avanzado"**. Puedes avanzar de forma incremental:

#### Paso 1: Levantar y familiarizarte con el stack de observabilidad
En tu máquina puedes ejecutar:
```bash
docker compose --profile observability up -d prometheus grafana
```
* Acceder a Prometheus (`http://localhost:9090`) y ver en **Status > Targets** qué está raspando actualmente.
* Acceder a Grafana (`http://localhost:3000`, usuario `admin`, clave `admin` en local) y verificar que el data source Prometheus ya esté conectado.

#### Paso 2: Auditar las métricas del monolito
* Levantar el backend principal y probar en el navegador o curl:
  `http://localhost:8080/actuator/prometheus`
* Identificar las métricas nativas que genera Micrometer:
  * Errores y llamadas HTTP: `http_server_requests_seconds_count` (filtrado por `status=~"4..|5.."`).
  * Latencia: `http_server_requests_seconds_max` o histogramas p95.

#### Paso 3: Preparar `audit-service` para métricas
* En el subproyecto [audit-service/backend](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/audit-service/backend):
  * Revisar si en su `pom.xml` falta `micrometer-registry-prometheus`.
  * En su [application.yml](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/resources/application.yml), el management endpoints solo tiene `include: health,info`. Necesitará `health,info,prometheus`.
  * Agregar el scrape job de `audit-backend:8081` en [prometheus.yml](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/ops/prometheus/prometheus.yml).

#### Paso 4: Diseñar y aprovisionar el Dashboard en Grafana
* Puedes armar el dashboard directamente en la interfaz web de Grafana consultando las métricas de Spring Actuator.
* Una vez que tengas los gráficos de 4xx/5xx, latencia y disponibilidad funcionando, exportas el JSON del dashboard.
* Lo guardas en la carpeta `ops/grafana/provisioning/dashboards/` configurando un archivo YAML de provisioning para que Grafana lo cargue automáticamente al iniciar (Infrastructure as Code).

#### Paso 5: Investigar las métricas de RabbitMQ y Kafka
* RabbitMQ viene con el plugin `rabbitmq_prometheus` preintegrado (normalmente escucha en el puerto `15692`). Puedes verificar cómo exponer ese puerto en [docker-compose.yml](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/docker-compose.yml) y rasparlo con Prometheus para obtener el conteo de mensajes en colas y DLQ sin necesidad de exporters externos pesados.
* Para Kafka, verificar si los clientes Spring (`spring.kafka.*`) ya exponen métricas de consumer lag a través de Micrometer.

---

### Resumen de dependencias / Qué postergar
* **No te bloquees por:**
  * **Alertas (Alertmanager):** Quedó formalmente fuera de alcance para esta HU.
  * **Tracing distribuido (OpenTelemetry / Jaeger):** Fuera de alcance por ahora.
  * **HU-28 (Métricas de IA):** Solo deja la estructura del dashboard lista; se integrará cuando esa historia se desarrolle.
  * **Mentorship Service si aún está en desarrollo:** Puedes simular o dejar el scrape configurado para cuando el servicio esté activo, entregando primero el monitoreo funcional de `backend`, `audit-service`, RabbitMQ y Prometheus/Grafana.
