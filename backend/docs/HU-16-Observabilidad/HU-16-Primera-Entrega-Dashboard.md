# HU-16 — Observabilidad de la Plataforma Híbrida (Primera Entrega)

## 1. Resumen de lo entregado
En esta primera fase se implementó la base de observabilidad multi-servicio sin alterar lógica de negocio ni bloquear otras HUs:

1. **Audit Service (`audit-service/backend`)**:
   - Se incorporó la dependencia `micrometer-registry-prometheus` en su [`pom.xml`](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/audit-service/backend/pom.xml).
   - Se configuró la exposición de métricas Actuator (`health,info,prometheus,metrics`) y el etiquetado común `application: audit-service` en [`application.yml`](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/resources/application.yml).
   - Compilación y dependencias verificadas exitosamente con Maven.

2. **Scraping centralizado en Prometheus**:
   - Se actualizó [`ops/prometheus/prometheus.yml`](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/ops/prometheus/prometheus.yml) para recolectar métricas tanto del monolito (`skillbridge-backend:8080`) como de `audit-backend:8081`.

3. **Dashboard unificado y aprovisionado en Grafana (IaC)**:
   - Se creó el proveedor automático en [`ops/grafana/provisioning/dashboards/dashboard-provider.yml`](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/ops/grafana/provisioning/dashboards/dashboard-provider.yml).
   - Se diseñó el dashboard [`ops/grafana/provisioning/dashboards/skillbridge-observability.json`](file:///D:/Juan-Cañas-D/Escritorio/DEV-RIWI-JAVA/Spring%20Boot/SkillBridge-AI-JAVA-Angular/ops/grafana/provisioning/dashboards/skillbridge-observability.json) con los siguientes paneles:
     - **Salud / Disponibilidad:** Estado UP/DOWN de los componentes activos.
     - **Errores HTTP:** Tasa de respuestas 4xx y 5xx separadas por servicio.
     - **Throughput:** Peticiones totales por segundo (RPS).
     - **Latencia:** Latencia máxima de endpoints HTTP (segundos).
     - **Colas / Asincronía:** Métricas reservadas para mensajes en colas y DLQ de RabbitMQ (`mentorship.payment.dlq`) y Kafka Consumer Lag.

---

## 2. Aclaraciones Técnicas Importantes

### A. ¿Por qué en `http://localhost:8080/actuator` no veo los números de las métricas directamente?
* La URL base `/actuator` es un **índice de descubrimiento (Discovery / HATEOAS)** que devuelve un JSON con los enlaces a las secciones disponibles (como `health`, `info`, `metrics`, `prometheus`).
* **Endpoints específicos de datos:**
  - `http://localhost:8080/actuator/prometheus` -> Muestra **todas las métricas en crudo** en el formato estándar que Prometheus lee y raspa periódicamente.
  - `http://localhost:8080/actuator/metrics` -> Lista los nombres de métricas individuales.
  - `http://localhost:8080/actuator/metrics/<nombre>` -> Ver detalle de una métrica en particular (ej. `http://localhost:8080/actuator/metrics/jvm.memory.used`).

### B. Diferencia entre `localhost` y nombres como `backend`
* **`backend:8080` / `audit-backend:8081`:** Son nombres de host internos de Docker. Solo funcionan entre contenedores dentro de la red privada de Docker (por ejemplo, cuando Prometheus va a leer al backend).
* **`localhost:8080` / `localhost:8081`:** Es la dirección para acceder desde tu navegador en Windows o Postman.

---

## 3. Ciclo de Vida: ¿Cómo levantar y apagar todo?

### Al encender el PC o iniciar una sesión de trabajo:

1. **Asegúrate de que Docker Desktop esté abierto y corriendo.**
2. **Levantar los servicios base (Bases de datos, brokers, backend):**
   ```powershell
   docker compose up -d
   ```
3. **Levantar el stack de observabilidad (Prometheus y Grafana):**
   Como Prometheus y Grafana usan el perfil `observability`, se activan con:
   ```powershell
   docker compose --profile observability up -d
   ```
   *(O si prefieres levantar todo de una sola vez en un solo comando: `docker compose --profile observability up -d`)*.

### Accesos locales en tu navegador:
* **Dashboard de Grafana:** [http://localhost:3000](http://localhost:3000) (Usuario: `admin`, Contraseña: `admin`).
  - Ir a: **Dashboards** > **SkillBridge — Observabilidad Multi-servicio**.
* **Objetivos de Prometheus (Status):** [http://localhost:9090/targets](http://localhost:9090/targets) (verifica que ambos servicios figuren en verde `UP`).
* **Métricas Backend:** [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus).
* **Métricas Audit Service:** [http://localhost:8081/actuator/prometheus](http://localhost:8081/actuator/prometheus).

### Para apagar los contenedores al terminar el día:
```powershell
docker compose --profile observability down
```
*(Los datos de configuración y los dashboards permanecen guardados en el código y en los volúmenes locales).*

---

## 4. Próximos pasos
1. Scraping del plugin de métricas de RabbitMQ (puerto 15692) para alimentar en tiempo real el panel de DLQs.
2. Verificación de la propagación del `X-Correlation-Id` desde peticiones HTTP hacia eventos RabbitMQ y Kafka para el rastreo de auditoría.
