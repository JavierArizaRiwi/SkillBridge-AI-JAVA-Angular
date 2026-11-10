# Guía de Despliegue en Servicios Cloud Gratuitos (Laboratorio)

Esta guía detalla el procedimiento paso a paso para desplegar **SkillBridge AI** en proveedores de nube gratuitos (Free Tier), permitiendo contar con un entorno público completamente operativo para demostraciones, pruebas y evaluación académica a costo $0.

Esta guía complementa la lista de verificación general en [`docs/DEPLOYMENT-CHECKLIST.md`](../DEPLOYMENT-CHECKLIST.md) y el registro de arquitectura en [`docs/adr/0002-managed-services-in-cloud.md`](../adr/0002-managed-services-in-cloud.md).

---

## 1. Topología Cloud Académica

```text
                         USUARIO
                           |
                           v
                    +-------------+
                    |   Vercel    |
                    |   Angular   |
                    +------+------+
                           |
                         HTTPS
                           |
                           v
                    +-------------+
                    |   Render    |
                    | Spring Boot |
                    +------+------+
                           |
          +----------------+------------------+----------------+
          |                |                  |                |
          v                v                  v                v
        Neon            Upstash           CloudAMQP         Gemini
      PostgreSQL         Redis             RabbitMQ           API
```

> [!NOTE]
> Esta topología prioriza el costo $0 para fines de formación. En un entorno empresarial de producción, estos componentes se sustituyen normalmente por Amazon RDS / Aurora, AWS ElastiCache, Amazon MQ / MSK, AWS ECS / EKS o equivalentes en GCP / Azure.

---

## 2. Aprovisionamiento de Servicios Gestionados

### 2.1 Base de Datos Relacional: Neon (PostgreSQL)

1. Regístrate en [Neon](https://neon.com).
2. Crea un nuevo proyecto PostgreSQL.
3. Dirígete a la sección **Connection Details**.
4. Obtén el host, base de datos, usuario y contraseña.
5. Construye la URL de conexión JDBC con soporte TLS obligatorio:
   ```text
   jdbc:postgresql://<HOST>/<DATABASE>?sslmode=require
   ```
6. Variables resultantes requeridas para el backend:
   ```env
   DATABASE_URL=jdbc:postgresql://<HOST>/<DATABASE>?sslmode=require
   DATABASE_USER=<USUARIO>
   DATABASE_PASSWORD=<PASSWORD>
   ```

### 2.2 Caché Distribuida: Upstash (Redis)

1. Regístrate en [Upstash](https://upstash.com).
2. Crea una base de datos Redis en plan **Free**. Selecciona una región geográficamente cercana a la instancia de cómputo (Render).
3. Copia la URL de conexión segura con esquema `rediss://` (TLS habilitado):
   ```text
   rediss://default:<PASSWORD>@<HOST>:<PORT>
   ```
4. Variable resultante para el backend:
   ```env
   REDIS_URL=rediss://default:<PASSWORD>@<HOST>:<PORT>
   ```

### 2.3 Broker de Mensajería: CloudAMQP (RabbitMQ)

1. Regístrate en [CloudAMQP](https://cloudamqp.com).
2. Crea una nueva instancia seleccionando el plan gratuito **Little Lemur**.
3. Abre los detalles de la instancia y copia la URL AMQP segura:
   ```text
   amqps://<USUARIO>:<PASSWORD>@<HOST>/<VHOST>
   ```
4. Variable resultante para el backend:
   ```env
   RABBITMQ_URL=amqps://<USUARIO>:<PASSWORD>@<HOST>/<VHOST>
   ```
5. Las colas declaradas por Spring Boot (`booking.created.queue`) son durables. Si la API gratuita entra en suspensión, los mensajes se retendrán en RabbitMQ hasta su reconexión.

### 2.4 Inteligencia Artificial: Google AI Studio (Gemini)

1. Accede a [Google AI Studio](https://aistudio.google.com/) con tu cuenta de Google.
2. Genera una API Key para uso en desarrollo/laboratorio.
3. Variable resultante para el backend:
   ```env
   GEMINI_API_KEY=<TU_API_KEY>
   GEMINI_MODEL=gemini-3.8-flash
   ```
4. El modelo es configurable dinámicamente mediante `GEMINI_MODEL` sin requerir cambios en el código de la aplicación.

---

## 3. Despliegue del Backend: Render (Web Service)

1. Sube el repositorio a tu cuenta de GitHub o GitLab.
2. En la consola de Render, selecciona **New -> Web Service** y conecta tu repositorio.
3. Configura los parámetros principales:
   - **Root Directory**: `backend`
   - **Runtime**: `Docker`
   - **Instance Type / Plan**: `Free`
   Render detectará automáticamente el archivo [`backend/Dockerfile`](../../backend/Dockerfile).
4. Configura las variables de entorno en la sección **Environment Variables**:
   ```env
   DATABASE_URL=jdbc:postgresql://<HOST>/<DATABASE>?sslmode=require
   DATABASE_USER=<USUARIO>
   DATABASE_PASSWORD=<PASSWORD>
   REDIS_URL=rediss://default:<PASSWORD>@<HOST>:<PORT>
   RABBITMQ_URL=amqps://<USUARIO>:<PASSWORD>@<HOST>/<VHOST>
   JWT_PRIVATE_KEY=<CLAVE_PRIVADA_RSA_PKCS8_PEM_EN_UNA_LINEA_CON_\n>
   JWT_GENERATE_EPHEMERAL_KEYS=false
   GEMINI_API_KEY=<TU_API_KEY>
   GEMINI_MODEL=gemini-3.8-flash
   CORS_ALLOWED_ORIGINS=https://<TU-FRONTEND>.vercel.app
   ```
   *Nota: Render asigna la variable `PORT` automáticamente, consumida por Spring Boot via `server.port=${PORT:8080}`.*
5. Configura el Health Check de Render:
   - **Health Check Path**: `/actuator/health/liveness`
   > [!IMPORTANT]
   > Se utiliza el probe de **liveness** para validar que el proceso Java está operativo sin inducir reinicios espurios si una dependencia externa (como Redis o RabbitMQ) experimenta latencia transitoria.
6. Pulsa **Deploy Web Service**. Una vez completado, obtendrás una URL pública como:
   ```text
   https://skillbridge-api.onrender.com
   ```
7. Verifica la salud del backend visitando:
   ```text
   https://skillbridge-api.onrender.com/actuator/health
   ```

> [!WARNING]
> **Comportamiento de Render Free (Cold Start)**:
> Los Web Services gratuitos en Render se suspenden tras 15 minutos de inactividad. La primera petición tras la inactividad puede tardar entre 50 y 90 segundos mientras el contenedor se reactiva. Esto es un comportamiento normal en tiers gratuitos.

---

## 4. Despliegue del Frontend: Vercel (SPA)

El frontend de Angular está diseñado para recibir configuración en tiempo de ejecución (`public/env.js` generado durante la compilación), lo que permite usar la misma base de código en cualquier entorno sin recompilar binarios con URLs cableadas.

1. En el panel de Vercel, selecciona **Add New -> Project** e importa el mismo repositorio.
2. Configura los parámetros de compilación:
   - **Framework Preset**: Angular
   - **Root Directory**: `frontend`
   El archivo [`frontend/vercel.json`](../../frontend/vercel.json) ya incluye las directivas para compilar el SPA y enrutar las rutas hacia `index.html`.
3. Configura la variable de entorno:
   ```env
   API_URL=https://skillbridge-api.onrender.com/api
   ```
   Durante `npm run build`, se ejecuta [`frontend/scripts/generate-env.mjs`](../../frontend/scripts/generate-env.mjs), inyectando dinámicamente `window.__env.API_URL`.
4. Pulsa **Deploy**. Obtendrás una URL pública como:
   ```text
   https://skillbridge-ai.vercel.app
   ```
5. **Ajuste de CORS en Render**:
   Regresa a las variables de entorno de tu servicio en Render y actualiza:
   ```env
   CORS_ALLOWED_ORIGINS=https://skillbridge-ai.vercel.app
   ```
   Reinicia o permite el redespliegue de Render para que la política de CORS admita peticiones desde el dominio de Vercel.

---

## 5. Decisión Arquitectónica: Nginx vs Vercel Edge

| Entorno | Estrategia de Servido Frontend | Rol de Nginx |
|---|---|---|
| **Local / Docker Compose** | Contenedor Nginx multi-stage | Sirve archivos estáticos de Angular y actúa como reverse proxy hacia `backend:8080` bajo `/api/`. |
| **Cloud Gratuito (Vercel)** | Vercel Edge Network / CDN global | Vercel sirve y cachea los estáticos en el borde; Angular realiza llamadas HTTPS directas hacia Render. Nginx no interviene en este escenario. |

> [!TIP]
> Si se desea evaluar la imagen Docker con Nginx en la nube, es posible desplegar [`frontend/Dockerfile`](../../frontend/Dockerfile) como un segundo Web Service en Render configurando la variable `BACKEND_URL=https://skillbridge-api.onrender.com`. Para cohortes académicas numerosas, la opción con Vercel es preferible porque no consume cuotas de horas de cómputo en Render.

---

## 6. Referencia de Cuotas y Condiciones de Servicios Gratuitos

Información de referencia de los tiers gratuitos de los proveedores (revisar siempre términos actualizados):

| Proveedor | Componente | Plan Gratuito | Consideraciones Operativas | Documentación Oficial |
|---|---|---|---|---|
| **Render** | Backend API | Free Web Service | Suspensión por inactividad tras 15 min; arranque en frío de ~50s; 750 horas de cómputo/mes. | [Render Free Plans](https://render.com/docs/free) |
| **Vercel** | Frontend SPA | Hobby | Ancho de banda y límites generosos para proyectos personales y demos. | [Vercel Pricing](https://vercel.com/pricing) |
| **Neon** | PostgreSQL | Free Tier | Almacenamiento limitado (0.5 GB); computación compartida con auto-suspend. | [Neon Pricing](https://neon.com/pricing) |
| **Upstash** | Redis Cache | Free Tier | Límite de 10.000 comandos diarios; persistencia y TLS incluidos. | [Upstash Redis Pricing](https://upstash.com/pricing/redis) |
| **CloudAMQP** | RabbitMQ | Little Lemur | Hasta 1.000.000 mensajes/mes; 20 conexiones concurrentes y 100 colas. | [CloudAMQP Plans](https://www.cloudamqp.com/plans.html) |
| **Google AI Studio** | Gemini AI | Developer Free | Cuotas RPM/RPD para modelos Flash; requiere API Key de proyecto. | [Gemini Pricing](https://ai.google.dev/gemini-api/docs/pricing) |
