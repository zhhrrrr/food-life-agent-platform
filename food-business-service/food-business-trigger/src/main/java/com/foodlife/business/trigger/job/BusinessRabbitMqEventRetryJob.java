package com.foodlife.business.trigger.job;

import com.foodlife.business.domain.event.IBusinessEventPublisher;
import com.foodlife.job.lock.ScheduledJobLockExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BusinessRabbitMqEventRetryJob {

    private static final String LOCK_KEY = "business:rabbitmq-event-retry";

    private final IBusinessEventPublisher businessEventPublisher;
    private final ScheduledJobLockExecutor jobLockExecutor;
    private final boolean enabled;
    private final int limit;

    public BusinessRabbitMqEventRetryJob(IBusinessEventPublisher businessEventPublisher,
                                         ScheduledJobLockExecutor jobLockExecutor,
                                         @Value("${food.jobs.business-event-retry.enabled:true}") boolean enabled,
                                         @Value("${food.jobs.business-event-retry.limit:50}") int limit) {
        this.businessEventPublisher = businessEventPublisher;
        this.jobLockExecutor = jobLockExecutor;
        this.enabled = enabled;
        this.limit = limit;
    }

    @Scheduled(fixedDelayString = "${food.jobs.business-event-retry.fixed-delay-ms:30000}")
    public void retryPendingBusinessEvents() {
        if (!enabled) {
            return;
        }
        jobLockExecutor.execute(LOCK_KEY, () -> businessEventPublisher.retryPendingEvents(limit));
    }
}
