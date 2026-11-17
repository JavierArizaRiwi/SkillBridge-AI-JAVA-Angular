# HU-14: Tarjetas de prueba principales (Stripe y Modo Simulado)

## 1. Tarjetas principales (Accesos rápidos en el Modal)

Estas son las cuatro tarjetas integradas en el modal de pago de SkillBridge para pruebas inmediatas en el navegador:

| Escenario | Número de tarjeta | CVC | Vencimiento | Resultado esperado |
| --- | --- | --- | --- | --- |
| **Pago exitoso** | `4242 4242 4242 4242` | `123` | Fecha futura (ej. `12/34`) | Pago aprobado inmediatamente (`succeeded`). |
| **Rechazo genérico** | `4000 0000 0000 0002` | `123` | Fecha futura (ej. `12/34`) | Rechazo genérico de tarjeta (`card_declined` / `generic_decline`). |
| **Fondos insuficientes** | `4000 0000 0000 9995` | `123` | Fecha futura (ej. `12/34`) | Rechazo por falta de fondos (`card_declined` / `insufficient_funds`). |
| **Autenticación 3D Secure** | `4000 0025 0000 3155` | `123` | Fecha futura (ej. `12/34`) | Despliega ventana emergente o modal de desafío 3DS. |

---

## 2. Pagos exitosos por marca principal

Para validar el reconocimiento visual o de red de las franquicias más utilizadas:

| Marca | Número de tarjeta | Longitud CVC |
| --- | --- | --- |
| **Visa** | `4242 4242 4242 4242` | 3 dígitos (ej. `123`) |
| **Mastercard** | `5555 5555 5555 4444` | 3 dígitos (ej. `123`) |
| **American Express** | `3782 822463 10005` | 4 dígitos (ej. `1234`) |

---

## 3. Principales errores y rechazos

Para probar el manejo de excepciones y mensajes de error en la interfaz:

| Caso de prueba | Número de tarjeta | Código Stripe | Descripción |
| --- | --- | --- | --- |
| **Rechazo genérico** | `4000 0000 0000 0002` | `card_declined` | Emisor rechaza la transacción. |
| **Fondos insuficientes** | `4000 0000 0000 9995` | `card_declined` | Sin fondos disponibles en la cuenta. |
| **Tarjeta vencida** | `4000 0000 0000 0069` | `expired_card` | Simula fecha de expiración caducada. |
| **CVC incorrecto** | `4000 0000 0000 0127` | `incorrect_cvc` | Falla en el código de seguridad. |
| **Error de procesamiento** | `4000 0000 0000 0119` | `processing_error` | Simula error temporal en la red bancaria. |

---

## 4. Reglas generales para pruebas con Stripe

- **Fecha de expiración**: Cualquier mes y año futuro (por ejemplo, `12/34`).
- **Código CVC**: Cualquier valor de 3 dígitos (o 4 dígitos en American Express).
- **Código Postal / ZIP**: Cualquier serie de 5 dígitos (por ejemplo, `12345` o `11011`).
- **Modo de claves**: Usar siempre claves de prueba (`pk_test_...` y `sk_test_...`). Nunca ingresar tarjetas reales en entornos de prueba.

---

## 5. Reglas del Modo Simulado (Fallback local sin Stripe)

Cuando `STRIPE_API_KEY` o `STRIPE_PUBLISHABLE_KEY` no están configuradas, SkillBridge opera en modo de pago simulado:

| Condición simulada | Entrada | Comportamiento |
| --- | --- | --- |
| **Rechazo forzado** | Tarjeta terminada en `0000` | Simula rechazo determinista por política de seguridad. |
| **CVV no permitido** | CVV igual a `000` | Rechazo por validación de seguridad de CVV. |
| **Tarjeta vencida** | Fecha anterior al mes actual | Validación en frontend/backend de expiración. |
| **Pago aprobado** | Cualquier otra combinación | Retorna `APPROVED` y confirma la reserva automáticamente. |

---

> Para consultar el catálogo extendido internacional, disputas avanzadas y pruebas de terminal físico, remitirse a la [documentación oficial de pruebas de Stripe](https://docs.stripe.com/testing).
