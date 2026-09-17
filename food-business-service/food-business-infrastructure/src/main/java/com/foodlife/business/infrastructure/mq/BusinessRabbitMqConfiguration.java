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
    public DirectExchange packageStockExchange() {
        return new DirectExchange(BusinessMqTopics.PACKAGE_STOCK_TOPIC, true, false);
    }

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
    public Queue packageStockEventQueue(BusinessRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getPackageStockEventQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getPackageStockEventDeadLetterQueue())
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
    public Queue packageStockEventDeadLetterQueue(BusinessRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getPackageStockEventDeadLetterQueue()).build();
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

    @Bean
    public Binding packageStockEventDeadLetterBinding(@Qualifier("packageStockEventDeadLetterQueue") Queue queue,
                                                      @Qualifier("businessDeadLetterExchange") DirectExchange exchange,
                                                      BusinessRabbitMqProperties properties) {
        return BindingBuilder.bind(queue).to(exchange).with(properties.getPackageStockEventDeadLetterQueue());
    }

    @Bean
    public Binding stockOccupiedEventBinding(@Qualifier("packageStockEventQueue") Queue packageStockEventQueue,
                                             @Qualifier("packageStockExchange") DirectExchange packageStockExchange) {
        return BindingBuilder.bind(packageStockEventQueue).to(packageStockExchange).with(BusinessMqTopics.STOCK_OCCUPIED);
    }

    @Bean
    public Binding stockReleasedEventBinding(@Qualifier("packageStockEventQueue") Queue packageStockEventQueue,
                                             @Qualifier("packageStockExchange") DirectExchange packageStockExchange) {
        return BindingBuilder.bind(packageStockEventQueue).to(packageStockExchange).with(BusinessMqTopics.STOCK_RELEASED);
    }

    @Bean
    public Binding stockSoldConfirmedEventBinding(@Qualifier("packageStockEventQueue") Queue packageStockEventQueue,
                                                  @Qualifier("packageStockExchange") DirectExchange packageStockExchange) {
        return BindingBuilder.bind(packageStockEventQueue).to(packageStockExchange).with(BusinessMqTopics.STOCK_SOLD_CONFIRMED);
    }

    @Bean
    public Binding stockRollbackEventBinding(@Qualifier("packageStockEventQueue") Queue packageStockEventQueue,
                                             @Qualifier("packageStockExchange") DirectExchange packageStockExchange) {
        return BindingBuilder.bind(packageStockEventQueue).to(packageStockExchange).with(BusinessMqTopics.STOCK_ROLLBACK);
    }

    @Bean
    public Binding searchStockOccupiedBinding(@Qualifier("businessSearchSyncQueue") Queue queue,
                                              @Qualifier("packageStockExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(BusinessMqTopics.STOCK_OCCUPIED);
    }

    @Bean
    public Binding searchStockReleasedBinding(@Qualifier("businessSearchSyncQueue") Queue queue,
                                              @Qualifier("packageStockExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(BusinessMqTopics.STOCK_RELEASED);
    }

    @Bean
    public Binding searchStockSoldConfirmedBinding(@Qualifier("businessSearchSyncQueue") Queue queue,
                                                   @Qualifier("packageStockExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(BusinessMqTopics.STOCK_SOLD_CONFIRMED);
    }

    @Bean
    public Binding searchStockRollbackBinding(@Qualifier("businessSearchSyncQueue") Queue queue,
                                              @Qualifier("packageStockExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(BusinessMqTopics.STOCK_ROLLBACK);
    }

    @Bean
    public Binding searchPackageChangedBinding(@Qualifier("businessSearchSyncQueue") Queue queue,
                                               @Qualifier("packageStockExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(BusinessMqTopics.PACKAGE_CHANGED);
    }
}
