
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';

import { createObserveModule } from '@nestjs/observe';

import { AppController } from './app.controller.js';
import { AppService } from './app.service.js';

import { RabbitmqService } from './rabbitmq/rabbitmq/rabbitmq.service.js';
import { RabbitmqModule } from './rabbitmq/rabbitmq.module.js';

import { MentorshipService } from './mentorship/mentorship/mentorship.service.js';
import { MentorshipModule } from './mentorship/mentorship/mentorship.module.js';

import { PaymentEventsModule } from './payment-events/payment-events.consumer/payment-events.module.js';

import { CommonModule } from './common/common.module.js';

export const { ObserveModule, ObserveInstrument } = createObserveModule();

@Module({
  imports: [
    // Environment variables
    ConfigModule.forRoot({
      isGlobal: true,
    }),

    // Database configuration
    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],

      useFactory: (config: ConfigService) => ({
        type: 'postgres',

        host: config.get<string>('DB_HOST'),
        port: Number(config.get<string>('DB_PORT', '5432')),
        username: config.get<string>('DB_USERNAME'),
        password: config.get<string>('DB_PASSWORD'),
        database: config.get<string>('DB_DATABASE'),

        autoLoadEntities: true,

        synchronize: false,

        retryAttempts: 5,
        retryDelay: 3000,

        logging: process.env.NODE_ENV === 'development',
      }),
    }),

    // Distributed tracing and observability
    ObserveModule.forRoot({
      appKey: process.env.OBSERVE_APP_KEY ?? 'YOUR_APP_KEY',
      appSecret: process.env.OBSERVE_APP_SECRET ?? 'YOUR_APP_SECRET',
      serviceId: 'backend-nest',
    }),

    // Application modules
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
  ],
})
export class AppModule {}
