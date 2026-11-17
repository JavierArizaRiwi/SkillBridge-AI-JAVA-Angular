import {
  type MiddlewareConsumer,
  Module,
  type NestModule,
  ValidationPipe,
} from '@nestjs/common';
import { APP_FILTER, APP_PIPE } from '@nestjs/core';
import { createObserveModule } from '@nestjs/observe';
import { AppController } from './app.controller.js';
import { AppService } from './app.service.js';
import { AuthModule } from './auth/auth.module.js';
import { CommonModule } from './common/common.module.js';
import { CorrelationMiddleware } from './common/correlation.middleware.js';
import { AllExceptionsFilter } from './common/filters/all-exceptions.filter.js';
import { AppConfigModule } from './config/config.module.js';
import { DatabaseModule } from './database/database.module.js';
import { MentorshipModule } from './mentorship/mentorship/mentorship.module.js';
import { MentorshipService } from './mentorship/mentorship/mentorship.service.js';
import { PaymentEventsModule } from './payment-events/payment-events.module.js';
import { RabbitmqModule } from './rabbitmq/rabbitmq.module.js';
import { RabbitmqService } from './rabbitmq/rabbitmq/rabbitmq.service.js';

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
    AppConfigModule,
    DatabaseModule,
    AuthModule,
    RabbitmqModule,
    PaymentEventsModule,
    MentorshipModule,
    CommonModule,
  ],
  controllers: [AppController],
  providers: [
    AppService,
    RabbitmqService,
    MentorshipService,
    {
      provide: APP_PIPE,
      useValue: new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    },
    { provide: APP_FILTER, useClass: AllExceptionsFilter },
  ],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer): void {
    consumer.apply(CorrelationMiddleware).forRoutes('*');
  }
}
