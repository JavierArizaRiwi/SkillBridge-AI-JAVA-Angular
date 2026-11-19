
export const RABBITMQ_CONFIG = {
    EXCHANGE: 'payment.events',
    EXCHANGE_TYPE: 'topic',

    ROUTING_KEY: 'payment.approved',

    QUEUES: {
        PAYMENT_APPROVED: 'mentorship.payment.approved.queue',
        RETRY_1S: 'mentorship.payment.retry.1s',
        RETRY_2S: 'mentorship.payment.retry.2s',
        RETRY_4S: 'mentorship.payment.retry.4s',
        DLQ: 'mentorship.payment.dlq',
    },

    DLX: {
        EXCHANGE: 'payment.dlx',
        ROUTING_KEY: 'payment.approved.dead',
    },

    MAX_RETRIES: 3,
};
