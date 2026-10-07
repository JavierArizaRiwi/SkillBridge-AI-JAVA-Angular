package com.riwi.skillbridge.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfiguration.class);

    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_BOOKING_CREATED_KEY = "notification.booking.created";
    public static final String NOTIFICATION_DLX = "notification.dlx";
    public static final String NOTIFICATION_DLQ = "notification.dlq";
    public static final String NOTIFICATION_DLQ_KEY = "notification.failed";

    @Bean
    TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(NOTIFICATION_DLX, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
            .deadLetterExchange(NOTIFICATION_DLX)
            .deadLetterRoutingKey(NOTIFICATION_DLQ_KEY)
            .build();
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue)
            .to(notificationExchange).with(NOTIFICATION_BOOKING_CREATED_KEY);
    }

    @Bean
    Binding notificationDlqBinding(Queue notificationDeadLetterQueue,
                                   DirectExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
            .to(notificationDeadLetterExchange).with(NOTIFICATION_DLQ_KEY);
    }

    @Bean
    MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplateCustomizer notificationRabbitTemplateCustomizer() {
        return template -> {
            template.setMandatory(true);
            template.setConfirmCallback((correlation, ack, cause) -> {
                String id = correlation != null ? correlation.getId() : "n/a";
                if (ack) {
                    log.debug("Broker confirmed eventId={}", id);
                } else {
                    log.error("Broker NACK eventId={} cause={}", id, cause);
                }
            });
            template.setReturnsCallback(returned -> log.error(
                "Unroutable message exchange={} routingKey={} reason={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        };
    }
}
