# Fase 2: Integración de Stripe Elements en Frontend (Angular)

## 1. Visión General y Arquitectura

En esta segunda fase se implementó la integración de la pasarela de pagos en el **Frontend (Angular)** utilizando **Stripe Elements** (`@stripe/stripe-js`), garantizando el cumplimiento normativo internacional de seguridad **PCI-DSS**.

### Principios Clave Aplicados:
* **PCI-DSS Compliance**: Ningún número de tarjeta, fecha de expiración o código de seguridad (CVV) es manipulado ni conocido por nuestro código JavaScript ni por nuestros servidores. En su lugar, Stripe Elements monta un iframe seguro y aislado directamente gestionado por Stripe.
* **Seguridad de las 3 Llaves**:
  * `STRIPE_PUBLISHABLE_KEY` (`pk_test_...`): Utilizada **únicamente en el frontend** para inicializar Stripe.js.
  * `STRIPE_SECRET_KEY` (`sk_test_...`): Mantenida exclusivamente en el backend para crear el `PaymentIntent`.
  * `STRIPE_WEBHOOK_SECRET` (`whsec_...`): Utilizada en el backend para validar criptográficamente los eventos de webhook.
* **Inyección Dinámica de Variables de Entorno**: A través del script `generate-env.mjs`, la clave pública de Stripe se inyecta en tiempo de despliegue o ejecución en `window.__env.STRIPE_PUBLISHABLE_KEY` (sin quemarla en el código fuente), permitiendo despliegues limpios en **localhost, Docker y Railway**.
* **Elegante Degradación / Fallback**: Si el entorno no cuenta con `STRIPE_PUBLISHABLE_KEY`, el modal de pagos cambia automáticamente y de forma transparente al **modo de prueba simulado**, preservando el funcionamiento original.

---

## 2. Componentes Creados y Modificados

### 1. `StripeService` (`frontend/src/app/core/stripe.service.ts`)
* Servicio Angular `@Injectable({ providedIn: 'root' })`.
* Encapsula la llamada a `loadStripe(publishableKey)`.
* Gestiona la instancia singleton de Stripe para optimizar la carga de scripts en el navegador.

### 2. `PaymentModal` (`frontend/src/app/features/payment-modal/`)
* **`payment-modal.ts`**:
  * En `ngOnInit()` solicita al backend el `client_secret` llamando a `/api/payments/authorize`.
  * Crea y monta el `PaymentElement` de Stripe con diseño armónico (`appearance`) usando la tipografía Inter y la paleta minimalista de SkillBridge.
  * Al pulsar "Confirmar pago", invoca `stripe.confirmPayment({ elements, confirmParams, redirect: 'if_required' })`.
  * Si el pago es exitoso (`status === 'succeeded'`), emite el evento `paymentSuccess` para actualizar la reserva.
  * Si no hay configuración de Stripe, ejecuta la validación y el envío simulado previo.
* **`payment-modal.html`**:
  * Mantiene la estructura del modal: Encabezado, políticas de cancelación, monto a pagar, estados de error y botones de acción.
  * En lugar de los campos de texto planos para tarjeta, renderiza el contenedor seguro: `<div #paymentElementContainer id="payment-element"></div>`.
  * Muestra un distintivo sutil: *"Pago seguro procesado por Stripe"*.

---

## 3. Pruebas Automatizadas

Se crearon y ejecutaron pruebas unitarias con **Karma / Jasmine** en modo headless:

* **`StripeService.spec.ts`**: 4 pruebas (creación, lectura de `window.__env`, manejo de clave ausente y retorno de instancia).
* **`PaymentModal.spec.ts`**: 4 pruebas (creación, activación de modo simulado cuando no hay credenciales, formateo de tarjeta y validación determinista de CVV '000').
* **Total Frontend**: **10 pruebas exitosas (0 fallos)** ejecutadas en Chrome Headless.

---

## 4. Configuración y Despliegue

### Variables de Entorno en el Frontend:
Para habilitar Stripe en tu desarrollo local:
```bash
# En el archivo .env o en tu terminal:
STRIPE_PUBLISHABLE_KEY=pk_test_tu_llave_publica_aqui
```
Al correr `npm start` o `npm run build`, el script `generate-env.mjs` generará `public/env.js`:
```javascript
window.__env = {
  "API_URL": "/api",
  "STRIPE_PUBLISHABLE_KEY": "pk_test_..."
};
```
En **Railway**, simplemente agrega `STRIPE_PUBLISHABLE_KEY` en las variables de entorno de tu servicio frontend.
