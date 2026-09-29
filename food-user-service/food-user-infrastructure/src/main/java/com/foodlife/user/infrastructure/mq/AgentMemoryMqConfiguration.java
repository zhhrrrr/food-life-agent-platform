package com.foodlife.user.infrastructure.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

@Configuration
public class AgentMemoryMqConfiguration {
    @Bean
    DirectExchange agentMemoryExchange(AgentMemoryMqProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    Queue agentMemoryQueue(AgentMemoryMqProperties properties) {
        return new Queue(properties.getQueue(), true, false, false, Map.of(
                "x-dead-letter-exchange", properties.getExchange(),
                "x-dead-letter-routing-key", properties.getDeadLetterRoutingKey()
        ));
    }

    @Bean
    Binding agentMemoryBinding(DirectExchange agentMemoryExchange,
                               @Qualifier("agentMemoryQueue") Queue agentMemoryQueue,
                               AgentMemoryMqProperties properties) {
        return BindingBuilder.bind(agentMemoryQueue).to(agentMemoryExchange).with(properties.getRoutingKey());
    }

    @Bean
    Queue agentMemoryDeadLetterQueue(AgentMemoryMqProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    Binding agentMemoryDeadLetterBinding(DirectExchange agentMemoryExchange,
                                         @Qualifier("agentMemoryDeadLetterQueue") Queue agentMemoryDeadLetterQueue,
                                         AgentMemoryMqProperties properties) {
        return BindingBuilder.bind(agentMemoryDeadLetterQueue)
                .to(agentMemoryExchange).with(properties.getDeadLetterRoutingKey());
    }
}
