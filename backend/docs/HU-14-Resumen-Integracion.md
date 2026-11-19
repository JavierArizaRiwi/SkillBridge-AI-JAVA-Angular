# Resumen de Integracion y API: HU-14 Simulacion Pasarela de Pago

## 1. Que se construyo en esta fase?

En esta fase nos enfocamos en conectar la Pasarela de Pagos Simulada con el Sistema de Reservas (Booking). Ademas, creamos el punto de entrada (API REST) para que el Frontend o Postman puedan iniciar todo el proceso.

Siguiendo la Arquitectura Hexagonal, tocamos las tres capas principales:

---

## 2. Capa de Aplicacion (application/)

Maneja la logica de negocio y la orquestacion.

### Nuevos Archivos:
* PaymentResultUseCase.java (Puerto de Entrada):
  - Ubicacion: application/port/in/
  - Proposito: Define el contrato que el modulo de Reservas expone para ser notificado sobre el resultado de un pago (processPaymentApproved y processPaymentRejected).

### Archivos Modificados:
* BookingService.java (Caso de Uso / Orquestador):
  - Ubicacion: application/service/
  - Proposito: Implementa PaymentResultUseCase. Si el pago es aprobado, pasa la reserva a CONFIRMED. Si es rechazado, la pasa a CANCELLED.
* BookingRepositoryPort.java (Puerto de Salida):
  - Ubicacion: application/port/out/
  - Proposito: Se anadio el metodo findById(UUID id) para buscar la reserva antes de actualizar su estado.

---

## 3. Capa de Infraestructura (infrastructure/)

Adaptadores que conectan la logica de negocio con el exterior.

### Nuevos Archivos (Adaptador de Entrada Web):
* PaymentController.java (Controlador REST):
  - Ubicacion: infrastructure/adapter/in/rest/
  - Proposito: Expone el endpoint POST /api/payments/authorize. Recibe los datos de pago ficticios y el header Idempotency-Key.
* PaymentRequestDto.java y PaymentResponseDto.java (DTOs):
  - Ubicacion: infrastructure/adapter/in/rest/dto/payment/
  - Proposito: Objetos para recibir la peticion HTTP y devolver la respuesta sin acoplar los modelos de dominio directamente a la web.

### Nuevos Archivos (Adaptador de Entrada de Mensajeria):
* PaymentResultConsumer.java (Listener de RabbitMQ):
  - Ubicacion: infrastructure/adapter/in/messaging/
  - Proposito: Escucha el exchange "payment.events" en RabbitMQ. Cuando llega un evento de pago, delega al BookingService la actualizacion de la reserva de forma asincrona.

### Archivos Modificados (Adaptador de Persistencia):
* BookingPersistenceAdapter.java (Adaptador JPA):
  - Ubicacion: infrastructure/adapter/out/persistence/
  - Proposito: Implementa el metodo findById usando JpaBookingRepository.
* SimulatedPaymentAdapter.java:
  - Ubicacion: infrastructure/adapter/out/payment/
  - Proposito: Se actualizo para retornar un paymentId generado (UUID) cumpliendo con el identificador ficticio de pago requerido.

---

## 4. Verificacion

Los tests unitarios cubren:
* Aprobacion determinista.
* Rechazo determinista por tarjeta terminada en 0000.
* Timeout simulado por CVV TIMEOUT.
* Error de sistema por CVV ERROR.
* Idempotencia evitando cobro doble y republicacion de eventos.
