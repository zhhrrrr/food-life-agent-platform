package com.foodlife.trade.trigger.job;

import com.foodlife.job.lock.ScheduledJobLockExecutor;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestRecoveryResult;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "food.trade.seckill.order-request-recovery", name = "enabled", havingValue = "true")
public class SeckillOrderRequestRecoveryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderRequestRecoveryJob.class);
    private static final String LOCK_KEY = "trade:seckill-order-request-recovery";

    private final OrderDomainService orderDomainService;
    private final ScheduledJobLockExecutor jobLockExecutor;

    public SeckillOrderRequestRecoveryJob(OrderDomainService orderDomainService,
                                          ScheduledJobLockExecutor jobLockExecutor) {
        this.orderDomainService = orderDomainService;
        this.jobLockExecutor = jobLockExecutor;
    }

    @Scheduled(fixedDelayString = "${food.trade.seckill.order-request-recovery.fixed-delay-ms:60000}")
    public void recoverRequests() {
        jobLockExecutor.execute(LOCK_KEY, this::doRecoverRequests);
    }

    private void doRecoverRequests() {
        SeckillOrderRequestRecoveryResult result = orderDomainService.recoverSeckillOrderRequests(null);
        if (result.getScannedMessageCount() > 0 || result.getCanceledRequestCount() > 0) {
            LOGGER.info("seckill order request recovery completed, scannedMessageCount={}, recoveredMessageCount={}, canceledRequestCount={}, releasedStockCount={}",
                    result.getScannedMessageCount(),
                    result.getRecoveredMessageCount(),
                    result.getCanceledRequestCount(),
                    result.getReleasedStockCount());
        }
    }
}
