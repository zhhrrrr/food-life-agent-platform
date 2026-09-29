package com.foodlife.user.infrastructure.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "food.agent-memory.mq")
public class AgentMemoryMqProperties {
    private boolean enabled = true;
    private String exchange = "food.agent.memory.sync.exchange";
    private String queue = "food.agent.memory.sync.queue";
    private String routingKey = "food.agent.memory.sync";
    private String deadLetterQueue = "food.agent.memory.sync.dlq";
    private String deadLetterRoutingKey = "food.agent.memory.sync.dead";
    private int retryLimit = 100;
    private int retryDelaySeconds = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getQueue() { return queue; }
    public void setQueue(String queue) { this.queue = queue; }
    public String getRoutingKey() { return routingKey; }
    public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
    public String getDeadLetterQueue() { return deadLetterQueue; }
    public void setDeadLetterQueue(String deadLetterQueue) { this.deadLetterQueue = deadLetterQueue; }
    public String getDeadLetterRoutingKey() { return deadLetterRoutingKey; }
    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) { this.deadLetterRoutingKey = deadLetterRoutingKey; }
    public int getRetryLimit() { return retryLimit; }
    public void setRetryLimit(int retryLimit) { this.retryLimit = retryLimit; }
    public int getRetryDelaySeconds() { return retryDelaySeconds; }
    public void setRetryDelaySeconds(int retryDelaySeconds) { this.retryDelaySeconds = retryDelaySeconds; }
}
