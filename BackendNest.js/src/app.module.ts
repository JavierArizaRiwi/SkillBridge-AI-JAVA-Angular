import { Module } from '@nestjs/common';
import { createObserveModule } from '@nestjs/observe';
import { AppController } from './app.controller.js';
import { AppService } from './app.service.js';
import { RabbitmqService } from './rabbitmq/rabbitmq/rabbitmq.service.js';
import { MentorshipService } from './mentorship/mentorship/mentorship.service.js';
import { RabbitmqModule } from './rabbitmq/rabbitmq.module.js';
import { PaymentEventsModule } from './payment-events/payment-events.module.js';
import { MentorshipModule } from './mentorship/mentorship/mentorship.module.js';
import { CommonModule } from './common/common.module.js';

export const { ObserveModule, ObserveInstrument } = createObserveModule();

@Module({
  imports: [
    // Distributed tracing, auto-correlated logs, request/job metrics, error
    // telemetry, alarms, and more — out of the box. Sign up at https://observe.nestjs.com,
    // then run `npx @nestjs/observe link` to write your key pair into .env.
    ObserveModule.forRoot({
      appKey: process.env.OBSERVE_APP_KEY ?? 'YOUR_APP_KEY',
      appSecret: process.env.OBSERVE_APP_SECRET ?? 'YOUR_APP_SECRET',
      serviceId: 'backend-nest',
    }),
    RabbitmqModule,
    PaymentEventsModule,
    MentorshipModule,
    CommonModule,
  ],
  controllers: [AppController],
  providers: [AppService, RabbitmqService, MentorshipService],
})
export class AppModule {}
