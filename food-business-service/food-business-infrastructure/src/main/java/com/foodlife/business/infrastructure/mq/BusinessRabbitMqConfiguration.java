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
    public Queue reviewCreatedQueue(BusinessRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getReviewCreatedQueue()).build();
    }

    @Bean
    public Queue packageStockEventQueue(BusinessRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getPackageStockEventQueue()).build();
    }

    @Bean
    public Binding reviewCreatedBinding(@Qualifier("reviewCreatedQueue") Queue reviewCreatedQueue,
                                        @Qualifier("shopReviewExchange") DirectExchange shopReviewExchange) {
        return BindingBuilder.bind(reviewCreatedQueue)
                .to(shopReviewExchange)
                .with(BusinessMqTopics.REVIEW_CREATED);
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
}
