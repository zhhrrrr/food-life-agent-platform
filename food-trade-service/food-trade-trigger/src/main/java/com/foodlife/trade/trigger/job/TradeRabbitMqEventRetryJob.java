package com.foodlife.trade.trigger.job;

import com.foodlife.job.lock.ScheduledJobLockExecutor;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TradeRabbitMqEventRetryJob {

    private static final String LOCK_KEY = "trade:rabbitmq-event-retry";

    private final ITradeEventPublisher tradeEventPublisher;
    private final ScheduledJobLockExecutor jobLockExecutor;
    private final boolean enabled;
    private final int limit;

    public TradeRabbitMqEventRetryJob(ITradeEventPublisher tradeEventPublisher,
                                      ScheduledJobLockExecutor jobLockExecutor,
                                      @Value("${food.jobs.trade-event-retry.enabled:true}") boolean enabled,
                                      @Value("${food.jobs.trade-event-retry.limit:50}") int limit) {
        this.tradeEventPublisher = tradeEventPublisher;
        this.jobLockExecutor = jobLockExecutor;
        this.enabled = enabled;
        this.limit = limit;
    }

    @Scheduled(fixedDelayString = "${food.jobs.trade-event-retry.fixed-delay-ms:30000}")
    public void retryPendingTradeEvents() {
        if (!enabled) {
            return;
        }
        jobLockExecutor.execute(LOCK_KEY, () -> tradeEventPublisher.retryPendingEvents(limit));
    }
}
