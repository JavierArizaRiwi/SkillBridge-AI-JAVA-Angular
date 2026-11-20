# Control de Archivos: HU-14 Simulacion Pasarela de Pago

## 1. Como verificar los cambios en Git?

Para ver exactamente que archivos nuevos se crearon y cuales se modificaron, ejecuta el siguiente comando en la raiz del proyecto:

```bash
git status -s
```

(O simplemente git status)

Al ejecutarlo veras:
* ?? (o en color rojo): Archivos nuevos que se crearon en la rama.
* M: Archivos que ya existian pero sufrieron algun cambio.

---

## 2. Lista completa de archivos involucrados

### Archivos Nuevos Creados:

1. **PaymentStatus.java**
   - Enum con los estados: PENDING, APPROVED, DECLINED, FAILED.
2. **PaymentCommand.java**
   - Record con los datos de entrada para procesar el pago ficticio.
3. **PaymentResult.java**
   - Record con la respuesta de la pasarela.
4. **PaymentApproved.java**
   - Evento de dominio para pago aprobado.
5. **PaymentRejected.java**
   - Evento de dominio para pago rechazado.
6. **AuthorizePaymentUseCase.java**
   - Puerto de entrada (interfaz del caso de uso de autorizacion).
7. **PaymentPort.java**
   - Puerto de salida para conectar la pasarela de pago.
8. **PaymentResultRepositoryPort.java**
   - Puerto de salida para persistencia de idempotencia.
9. **PaymentEventPublisherPort.java**
   - Puerto de salida para publicar eventos.
10. **PaymentAuthorizationService.java**
    - Servicio de aplicacion que orquesta el pago, la idempotencia y los eventos.
11. **SimulatedPaymentAdapter.java**
    - Adaptador de salida que implementa las reglas deterministas de pago.
12. **InMemoryPaymentResultRepositoryAdapter.java**
    - Adaptador en memoria para guardar las llaves de idempotencia.
13. **RabbitPaymentEventPublisher.java**
    - Adaptador de salida para publicar eventos a RabbitMQ.

---

### Archivos Existentes Modificados:

1. **RabbitConfiguration.java**
   - Se anadio la configuracion del exchange "payment.events" para enrutar los eventos de pago.

---
Nota: Ejecuta git status en la terminal para verificar todos estos archivos.
