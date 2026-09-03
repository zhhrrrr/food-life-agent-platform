package com.foodlife.trade.infrastructure.mq;

import com.foodlife.trade.domain.order.event.TradeMqTopics;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "food.mq", name = "enabled", havingValue = "true")
public class TradeRabbitMqConfiguration {

    @Bean
    public DirectExchange tradeOrderExchange() {
        return new DirectExchange(TradeMqTopics.TRADE_ORDER_TOPIC, true, false);
    }

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(TradeMqTopics.PAYMENT_TOPIC, true, false);
    }

    @Bean
    public Queue orderTimeoutDelayQueue(TradeRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getOrderTimeoutDelayQueue())
                .deadLetterExchange(TradeMqTopics.TRADE_ORDER_TOPIC)
                .deadLetterRoutingKey(TradeMqTopics.ORDER_CANCEL_TIMEOUT)
                .build();
    }

    @Bean
    public Queue orderTimeoutCloseQueue(TradeRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getOrderTimeoutCloseQueue()).build();
    }

    @Bean
    public Queue tradeOrderEventQueue(TradeRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getTradeOrderEventQueue()).build();
    }

    @Bean
    public Queue paymentEventQueue(TradeRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getPaymentEventQueue()).build();
    }

    @Bean
    public Binding orderTimeoutDelayBinding(@Qualifier("orderTimeoutDelayQueue") Queue orderTimeoutDelayQueue,
                                            @Qualifier("tradeOrderExchange") DirectExchange tradeOrderExchange,
                                            TradeRabbitMqProperties properties) {
        return BindingBuilder.bind(orderTimeoutDelayQueue)
                .to(tradeOrderExchange)
                .with(properties.getOrderTimeoutDelayRoutingKey());
    }

    @Bean
    public Binding orderTimeoutCloseBinding(@Qualifier("orderTimeoutCloseQueue") Queue orderTimeoutCloseQueue,
                                            @Qualifier("tradeOrderExchange") DirectExchange tradeOrderExchange) {
        return BindingBuilder.bind(orderTimeoutCloseQueue)
                .to(tradeOrderExchange)
                .with(TradeMqTopics.ORDER_CANCEL_TIMEOUT);
    }

    @Bean
    public Binding orderCreatedEventBinding(@Qualifier("tradeOrderEventQueue") Queue tradeOrderEventQueue,
                                            @Qualifier("tradeOrderExchange") DirectExchange tradeOrderExchange) {
        return BindingBuilder.bind(tradeOrderEventQueue).to(tradeOrderExchange).with(TradeMqTopics.ORDER_CREATED);
    }

    @Bean
    public Binding orderPaidEventBinding(@Qualifier("tradeOrderEventQueue") Queue tradeOrderEventQueue,
                                         @Qualifier("tradeOrderExchange") DirectExchange tradeOrderExchange) {
        return BindingBuilder.bind(tradeOrderEventQueue).to(tradeOrderExchange).with(TradeMqTopics.ORDER_PAID);
    }

    @Bean
    public Binding orderRefundRequestedEventBinding(@Qualifier("tradeOrderEventQueue") Queue tradeOrderEventQueue,
                                                    @Qualifier("tradeOrderExchange") DirectExchange tradeOrderExchange) {
        return BindingBuilder.bind(tradeOrderEventQueue).to(tradeOrderExchange).with(TradeMqTopics.ORDER_REFUND_REQUESTED);
    }

    @Bean
    public Binding orderUsedEventBinding(@Qualifier("tradeOrderEventQueue") Queue tradeOrderEventQueue,
                                         @Qualifier("tradeOrderExchange") DirectExchange tradeOrderExchange) {
        return BindingBuilder.bind(tradeOrderEventQueue).to(tradeOrderExchange).with(TradeMqTopics.ORDER_USED);
    }

    @Bean
    public Binding orderTimeoutEventBinding(@Qualifier("tradeOrderEventQueue") Queue tradeOrderEventQueue,
                                            @Qualifier("tradeOrderExchange") DirectExchange tradeOrderExchange) {
        return BindingBuilder.bind(tradeOrderEventQueue).to(tradeOrderExchange).with(TradeMqTopics.ORDER_CANCEL_TIMEOUT);
    }

    @Bean
    public Binding paymentCreatedEventBinding(@Qualifier("paymentEventQueue") Queue paymentEventQueue,
                                              @Qualifier("paymentExchange") DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentEventQueue).to(paymentExchange).with(TradeMqTopics.PAYMENT_CREATED);
    }

    @Bean
    public Binding paymentSuccessEventBinding(@Qualifier("paymentEventQueue") Queue paymentEventQueue,
                                              @Qualifier("paymentExchange") DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentEventQueue).to(paymentExchange).with(TradeMqTopics.PAYMENT_SUCCESS);
    }

    @Bean
    public Binding paymentClosedEventBinding(@Qualifier("paymentEventQueue") Queue paymentEventQueue,
                                             @Qualifier("paymentExchange") DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentEventQueue).to(paymentExchange).with(TradeMqTopics.PAYMENT_CLOSED);
    }

    @Bean
    public Binding paymentRefundedEventBinding(@Qualifier("paymentEventQueue") Queue paymentEventQueue,
                                               @Qualifier("paymentExchange") DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentEventQueue).to(paymentExchange).with(TradeMqTopics.PAYMENT_REFUNDED);
    }
}
