# Resumen de Tests: HU-14 Simulacion Pasarela de Pago

## 1. Descripcion General
Se implementaron las pruebas unitarias exigidas en los Criterios de Aceptacion para garantizar que el comportamiento determinista de la pasarela y la idempotencia funcionen adecuadamente sin necesidad de conexion real a servicios externos.

---

## 2. Clases de Prueba Implementadas

Ubicacion base: backend/src/test/java/com/riwi/skillbridge/

### A. SimulatedPaymentAdapterTest
* Ubicacion: infrastructure/adapter/out/payment/SimulatedPaymentAdapterTest.java
* Proposito: Valida las reglas simuladas del proveedor de pago.
* Metodos de prueba:
  1. testApprovePayment(): Valida aprobacion determinista (tarjeta valida y CVV normal). Espera estado APPROVED.
  2. testDeclineCard0000(): Valida rechazo determinista cuando la tarjeta termina en 0000. Espera estado DECLINED.
  3. testSimulateTimeout(): Valida timeout simulado cuando el CVV es TIMEOUT. Verifica que ocurra un retraso de al menos 2000ms y retorne DECLINED.
  4. testSimulateError(): Valida error de sistema cuando el CVV es ERROR. Verifica que lance RuntimeException.

### B. PaymentAuthorizationServiceTest
* Ubicacion: application/service/PaymentAuthorizationServiceTest.java
* Proposito: Valida la logica de negocio, idempotencia y publicacion de eventos mockeando dependencias.
* Metodos de prueba:
  1. testIdempotencyRepeat(): Si la Idempotency-Key ya existe en memoria, retorna el resultado previo sin invocar a la pasarela ni publicar nuevos eventos.
  2. testPaymentApproved(): Si el pago es nuevo y resulta aprobado, guarda el resultado y publica el evento PaymentApproved.
  3. testPaymentDeclined(): Si el pago es nuevo y resulta rechazado, guarda el resultado y publica el evento PaymentRejected.

---

## 3. Resultado de la Ejecucion

Comando ejecutado:
```bash
mvn test "-Dtest=SimulatedPaymentAdapterTest,PaymentAuthorizationServiceTest"
```

Resultado:
* Total de tests ejecutados: 7
* Fallos: 0
* Errores: 0
* Estado: BUILD SUCCESS
