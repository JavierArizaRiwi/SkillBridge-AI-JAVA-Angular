import {
    Controller,
    Logger,
} from '@nestjs/common';

import {
    Ctx,
    EventPattern,
    Payload,
    RmqContext,
} from '@nestjs/microservices';

import { validate } from 'class-validator';
import { plainToInstance } from 'class-transformer';

import { PaymentApprovedService } from './payment-approved.service.js';
import { PaymentApprovedDto } from '../dto/payment-approved.dto.js';
import { MentorshipModule } from '../payment-events.consumer/payment-events.module.js';
import { MentorshipMode } from '../interfaces/payment-approvend.interface.js';

@Controller()
export class PaymentEventsConsumer {
    private readonly logger = new Logger(
    PaymentEventsConsumer.name,
);

constructor(
    private readonly paymentApprovedService: PaymentApprovedService,
) {}

@EventPattern('payment.approved')
async handlePaymentApproved(
    @Payload() payload: unknown,
    @Ctx() context: unknown,
): Promise<void> {
    const rmqContext = context as RmqContext;
    const channel = rmqContext.getChannelRef();
    const message = rmqContext.getMessage();

    try {
      // 1. Convertir el mensaje recibido en nuestro DTO
    const event = plainToInstance(
        PaymentApprovedDto,
        payload,
    );

      // 2. Validar los datos
    const errors = await validate(event);

    if (errors.length > 0) {
        this.logger.error(
        `Evento inválido: ${JSON.stringify(errors)}`,
        );

        // Enviar mensaje inválido a la DLQ mediante la configuración
        // de Dead Letter Exchange de RabbitMQ.
        channel.nack(message, false, false);
        return;
    }

      // 3. Procesar el pago aprobado
    const paymentEvent = {
        ...event,
        mode: event.mode as unknown as MentorshipMode,
    };

    await this.paymentApprovedService.process(paymentEvent);

      // 4. Confirmar que el mensaje fue procesado
    channel.ack(message);

    this.logger.log(
        `Pago aprobado procesado: ${event.bookingId}`,
    );
    } catch (error) {
    this.logger.error(
        'Error procesando el evento PaymentApproved',
        error instanceof Error ? error.stack : String(error),
    );

      // Rechazar sin reencolar inmediatamente.
      // La estrategia de reintentos debe estar configurada
      // mediante colas TTL y Dead Letter Exchange.
    channel.nack(message, false, false);
    }
  }
}