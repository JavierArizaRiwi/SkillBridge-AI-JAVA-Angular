
import {
    Injectable,
    Logger,
    OnModuleDestroy,
    OnModuleInit,
} from '@nestjs/common';

import { ConfigService } from '@nestjs/config';
import * as amqp from 'amqplib';

import { RABBITMQ_CONFIG } from './rabbitmq.constants.js';

@Injectable()
export class RabbitmqService
implements OnModuleInit, OnModuleDestroy
{
private readonly logger = new Logger(RabbitmqService.name);

    private connection: amqp.ChannelModel;
    private channel: amqp.Channel;

constructor(private readonly configService: ConfigService) {}

async onModuleInit() {
    await this.connect();
}

async connect() {
    const url = this.configService.get<string>('RABBITMQ_URL');

    if (!url) {
    throw new Error('RABBITMQ_URL no está configurada');
    }

    this.connection = await amqp.connect(url);

    this.channel = await this.connection.createChannel();

    await this.channel.prefetch(10);

    await this.setupTopology();

    this.logger.log('Conectado correctamente a RabbitMQ');

    this.connection.on('error', (error) => {
    this.logger.error('Error de conexión RabbitMQ', error);
    });

    this.connection.on('close', () => {
    this.logger.warn('Conexión RabbitMQ cerrada');
    });
}

private async setupTopology() {
    const config = RABBITMQ_CONFIG;

    // Exchange principal
    await this.channel.assertExchange(
    config.EXCHANGE,
    config.EXCHANGE_TYPE,
    { durable: true },
    );

    // Exchange de mensajes fallidos
    await this.channel.assertExchange(
    config.DLX.EXCHANGE,
    'direct',
    { durable: true },
    );

    // Cola principal
    await this.channel.assertQueue(
    config.QUEUES.PAYMENT_APPROVED,
    { durable: true },
    );

    await this.channel.bindQueue(
    config.QUEUES.PAYMENT_APPROVED,
    config.EXCHANGE,
    config.ROUTING_KEY,
    );

    // Colas de reintentos
    const retries = [
    { name: config.QUEUES.RETRY_1S, ttl: 1000 },
    { name: config.QUEUES.RETRY_2S, ttl: 2000 },
    { name: config.QUEUES.RETRY_4S, ttl: 4000 },
    ];

    for (const retry of retries) {
    await this.channel.assertQueue(retry.name, {
        durable: true,
        arguments: {
        'x-message-ttl': retry.ttl,
        'x-dead-letter-exchange': config.EXCHANGE,
        'x-dead-letter-routing-key': config.ROUTING_KEY,
        },
    });
    }

    // Dead Letter Queue
    await this.channel.assertQueue(config.QUEUES.DLQ, {
    durable: true,
    });

    await this.channel.bindQueue(
    config.QUEUES.DLQ,
    config.DLX.EXCHANGE,
    config.DLX.ROUTING_KEY,
    );

    this.logger.log('Exchanges y colas configurados correctamente');
}

getChannel(): amqp.Channel {
    if (!this.channel) {
    throw new Error('RabbitMQ no está conectado');
    }

    return this.channel;
}

async publishRetry(
    message: amqp.ConsumeMessage,
    retryCount: number,
) {
    const queues = [
    RABBITMQ_CONFIG.QUEUES.RETRY_1S,
    RABBITMQ_CONFIG.QUEUES.RETRY_2S,
    RABBITMQ_CONFIG.QUEUES.RETRY_4S,
    ];

    const queue = queues[retryCount - 1];

    if (!queue) {
    throw new Error('Número de reintentos inválido');
    }

    const headers = {
    ...(message.properties.headers || {}),
    'x-retry-count': retryCount,
    };

    const published = this.channel.sendToQueue(
    queue,
    message.content,
    {
        persistent: true,
        contentType: 'application/json',
        headers,
        correlationId: message.properties.correlationId,
        messageId: message.properties.messageId,
    },
    );

    if (!published) {
    this.logger.warn('RabbitMQ alcanzó el límite de escritura');
    }

    this.logger.warn(
    `Mensaje enviado a reintento ${retryCount}: ${queue}`,
    );
}

async publishToDLQ(
    message: amqp.ConsumeMessage,
    error: string,
) {
    const headers = {
    ...(message.properties.headers || {}),
    'x-final-error': error,
    };

    const published = this.channel.publish(
    RABBITMQ_CONFIG.DLX.EXCHANGE,
    RABBITMQ_CONFIG.DLX.ROUTING_KEY,
    message.content,
    {
        persistent: true,
        contentType: 'application/json',
        headers,
        correlationId: message.properties.correlationId,
        messageId: message.properties.messageId,
    },
    );

    if (!published) {
    this.logger.warn('RabbitMQ alcanzó el límite de escritura');
    }

    this.logger.error('Mensaje enviado a Dead Letter Queue');
}

async onModuleDestroy() {
    if (this.channel) {
    await this.channel.close();
    }

    if (this.connection) {
    await this.connection.close();
    }

    this.logger.log('Conexión RabbitMQ cerrada');
}
}
