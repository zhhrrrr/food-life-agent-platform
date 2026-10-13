package com.foodlife.business.infrastructure.mq;

import com.foodlife.business.domain.event.BusinessMqTopics;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "food.mq", name = "enabled", havingValue = "true")
public class BusinessRabbitMqConfiguration {

    @Bean
    public DirectExchange shopReviewExchange() {
        return new DirectExchange(BusinessMqTopics.SHOP_REVIEW_TOPIC, true, false);
    }

    @Bean
    public DirectExchange businessDeadLetterExchange(BusinessRabbitMqProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue reviewCreatedQueue(BusinessRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getReviewCreatedQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getReviewCreatedDeadLetterQueue())
                .build();
    }

    @Bean
    public Queue businessSearchSyncQueue(BusinessRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getSearchSyncQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getSearchSyncDeadLetterQueue())
                .build();
    }

    @Bean
    public Queue reviewCreatedDeadLetterQueue(BusinessRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getReviewCreatedDeadLetterQueue()).build();
    }

    @Bean
    public Queue businessSearchSyncDeadLetterQueue(BusinessRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getSearchSyncDeadLetterQueue()).build();
    }

    @Bean
    public Binding reviewCreatedBinding(@Qualifier("reviewCreatedQueue") Queue reviewCreatedQueue,
                                        @Qualifier("shopReviewExchange") DirectExchange shopReviewExchange) {
        return BindingBuilder.bind(reviewCreatedQueue)
                .to(shopReviewExchange)
                .with(BusinessMqTopics.REVIEW_CREATED);
    }

    @Bean
    public Binding reviewCreatedDeadLetterBinding(@Qualifier("reviewCreatedDeadLetterQueue") Queue queue,
                                                  @Qualifier("businessDeadLetterExchange") DirectExchange exchange,
                                                  BusinessRabbitMqProperties properties) {
        return BindingBuilder.bind(queue).to(exchange).with(properties.getReviewCreatedDeadLetterQueue());
    }

    @Bean
    public Binding businessSearchSyncDeadLetterBinding(@Qualifier("businessSearchSyncDeadLetterQueue") Queue queue,
                                                       @Qualifier("businessDeadLetterExchange") DirectExchange exchange,
                                                       BusinessRabbitMqProperties properties) {
        return BindingBuilder.bind(queue).to(exchange).with(properties.getSearchSyncDeadLetterQueue());
    }

    @Bean
    public Binding searchReviewCreatedBinding(@Qualifier("businessSearchSyncQueue") Queue queue,
                                              @Qualifier("shopReviewExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(BusinessMqTopics.REVIEW_CREATED);
    }

    @Bean
    public Binding searchReviewHiddenBinding(@Qualifier("businessSearchSyncQueue") Queue queue,
                                             @Qualifier("shopReviewExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(BusinessMqTopics.REVIEW_HIDDEN);
    }

}
