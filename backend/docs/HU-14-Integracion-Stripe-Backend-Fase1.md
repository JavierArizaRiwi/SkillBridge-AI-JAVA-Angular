# Fase 1: Integración de Pasarela de Pagos Stripe en Backend (Spring Boot)

## 1. Visión General y Arquitectura

En esta primera fase se implementó la integración con la pasarela real de **Stripe** en "Test Mode" (Región EE. UU.), complementando el flujo de simulación local existente bajo los principios de la **Arquitectura Hexagonal (Ports & Adapters)** y la arquitectura dirigida por eventos (**Event-Driven Architecture**).

### Principios Clave Aplicados:
* **Separación de Responsabilidades e Inversión de Dependencias**: El núcleo de la aplicación (`domain` y `application`) desconoce la implementación técnica de Stripe. Se comunica únicamente a través de los puertos existentes (`PaymentPort`, `PaymentEventPublisherPort`).
* **Cliente HTTP Declarativo**: Se utilizó **Spring Cloud OpenFeign** en lugar del SDK de Stripe para la creación de `PaymentIntent`, consumiendo directamente la API REST `https://api.stripe.com/v1/payment_intents`.
* **Tolerancia a Fallos y Resiliencia**: Se integró **Resilience4j** (`@CircuitBreaker` y `@Retry`) envolviendo las llamadas de red hacia Stripe para garantizar estabilidad ante latencias o caídas de conectividad.
* **Seguridad Criptográfica**: El SDK oficial `stripe-java` se incorporó de forma **exclusiva** para la verificación criptográfica estricta de las firmas de los Webhooks (`Webhook.constructEvent`), garantizando autenticidad y protegiendo contra ataques de repetición (replay attacks).
* **Seguridad en Spring Security**: Se habilitó la ruta `/api/webhooks/**` en `SecurityConfiguration` como pública (`permitAll`) y exenta de protección CSRF para permitir la recepción de eventos asíncronos desde Stripe CLI o los servidores de Stripe.

---

## 2. Mapa de Componentes y Clases Creadas / Modificadas

### Modificaciones en el Dominio y Aplicación:
* **`PaymentResult.java`**: Se enriqueció el record para incluir `String clientSecret` sin romper la compatibilidad hacia atrás (constructor sobrecargado para 4 argumentos conservado intacto para tests y simulación previa).
* **`PaymentAuthorizationService.java`**: Actualizado para soportar el estado `PENDING`. Si un `PaymentIntent` es creado en estado pendiente de confirmación en el cliente, guarda el resultado sin emitir falsos rechazos, delegando la confirmación definitiva al Webhook asíncrono.
* **`PaymentResponseDto.java` & `PaymentController.java`**: Actualizados para exponer el `clientSecret` al cliente consumidor (necesario para la Fase 2 en Angular con Stripe Elements).

### Nuevos Adaptadores de Salida (Secondary / Driven Adapters):
* **`StripePaymentIntentResponse.java`**: DTO tipado para deserializar la respuesta REST de Stripe (`id`, `client_secret`, `status`, `metadata`, etc.).
* **`StripeFeignConfig.java`**: Interceptor Feign que inyecta dinámicamente el header `Authorization: Bearer <STRIPE_SECRET_KEY>`.
* **`StripeFeignClient.java`**: Interfaz OpenFeign que realiza `POST /v1/payment_intents` con `consumes = application/x-www-form-urlencoded`.
* **`StripePaymentAdapter.java`**:
  * Implementa `PaymentPort`.
  * Habilitado por defecto mediante `@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe", matchIfMissing = true)`.
  * Convierte el monto decimal a centavos enteros (ej. `$50.00` -> `5000` centavos USD).
  * Protegido con `@CircuitBreaker(name = "stripePaymentClient")` y `@Retry(name = "stripePaymentClient")`.
  * Método fallback `fallbackProcess` para degradación elegante ante caídas de la pasarela.

### Nuevo Adaptador de Entrada (Primary / Driving Adapter):
* **`StripeWebhookController.java`**:
  * Expone `POST /api/webhooks/stripe`.
  * Recibe el payload en crudo (`String`) y el header `Stripe-Signature`.
  * Valida criptográficamente la firma con `Webhook.constructEvent(payload, sigHeader, webhookSecret)`.
  * Ante evento `payment_intent.succeeded`: Extrae el `bookingId` de los metadatos y emite `PaymentApproved` a RabbitMQ.
  * Ante evento `payment_intent.payment_failed`: Extrae el motivo del fallo y emite `PaymentRejected` a RabbitMQ.
  * Enlaza automáticamente con `PaymentResultConsumer` y `BookingService` para confirmar la reserva en el sistema.

---

## 3. Variables de Entorno y Configuración

Configuración centralizada en `application.yml` y respaldada por variables de entorno:

| Variable de Entorno | Descripción | Valor Local (Ejemplo) | Valor Railway / Producción |
| :--- | :--- | :--- | :--- |
| `STRIPE_SECRET_KEY` | Llave secreta de Stripe para la API | `sk_test_51...` | `sk_test_...` (o `sk_live_...`) |
| `STRIPE_WEBHOOK_SECRET` | Secreto del Webhook para validación criptográfica | Obtenido de `stripe listen` | `whsec_...` (configurado en Stripe Dashboard) |
| `STRIPE_API_URL` | URL base de la API de Stripe | `https://api.stripe.com` | `https://api.stripe.com` |
| `STRIPE_CURRENCY` | Moneda estándar ISO 3 letras | `usd` | `usd` |
| `PAYMENT_PROVIDER` | Proveedor activo (`stripe` o `simulated`) | `stripe` | `stripe` |

---

## 4. Guía de Ejecución y Pruebas en Local (Stripe CLI)

### 1. Iniciar el Backend
Asegúrate de que tus variables de entorno estén presentes en tu entorno o en el `.env`:
```bash
export STRIPE_SECRET_KEY=sk_test_tu_llave_aqui
export STRIPE_WEBHOOK_SECRET=whsec_tu_webhook_secret_aqui
mvn spring-boot:run
```

### 2. Escuchar Eventos con Stripe CLI
Para recibir los webhooks en tu máquina local sin necesidad de abrir puertos públicamente:
```bash
stripe listen --forward-to localhost:8080/api/webhooks/stripe
```
El comando mostrará en consola:
```text
> Ready! Your webhook signing secret is whsec_xxxxxxxxxxxxxxxxxxxxxxxxxxxx
```
Copia ese valor en tu variable de entorno `STRIPE_WEBHOOK_SECRET`.

### 3. Disparar Eventos de Prueba con Stripe CLI
En otra terminal, puedes disparar eventos simulados directamente:
```bash
stripe trigger payment_intent.succeeded
stripe trigger payment_intent.payment_failed
```

---

## 5. Pruebas Automatizadas

Se crearon pruebas unitarias específicas y se validó la compatibilidad de toda la suite:

* **`PaymentAuthorizationServiceTest`**: 3 tests (Pasan en verde al 100%, validando idempotencia y emisión de eventos).
* **`SimulatedPaymentAdapterTest`**: 4 tests (Pasan en verde al 100%, validando determinismo y timeouts).
* **`StripePaymentAdapterTest`**: 3 tests (Validan conversión de centavos, envío de metadata, idempotencia y activación del fallback de Resilience4j).
* **`StripeWebhookControllerTest`**: 5 tests (Validan rechazo de firmas faltantes e inválidas con 400, verificación criptográfica exitosa, emisión de `PaymentApproved`, emisión de `PaymentRejected` y manejo seguro de payloads sin metadatos).

Total: **15 tests ejecutados exitosamente con 0 fallos**.
