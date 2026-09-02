package com.foodlife.trade.trigger.job;

import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TradeRocketMqEventRetryJob {

    private final ITradeEventPublisher tradeEventPublisher;
    private final boolean enabled;
    private final int limit;

    public TradeRocketMqEventRetryJob(ITradeEventPublisher tradeEventPublisher,
                                      @Value("${food.jobs.trade-event-retry.enabled:true}") boolean enabled,
                                      @Value("${food.jobs.trade-event-retry.limit:50}") int limit) {
        this.tradeEventPublisher = tradeEventPublisher;
        this.enabled = enabled;
        this.limit = limit;
    }

    @Scheduled(fixedDelayString = "${food.jobs.trade-event-retry.fixed-delay-ms:30000}")
    public void retryPendingTradeEvents() {
        if (!enabled) {
            return;
        }
        tradeEventPublisher.retryPendingEvents(limit);
    }
}

