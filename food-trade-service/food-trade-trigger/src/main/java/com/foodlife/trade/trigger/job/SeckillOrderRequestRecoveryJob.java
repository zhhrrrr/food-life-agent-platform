package com.foodlife.trade.trigger.job;

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

    private final OrderDomainService orderDomainService;

    public SeckillOrderRequestRecoveryJob(OrderDomainService orderDomainService) {
        this.orderDomainService = orderDomainService;
    }

    @Scheduled(fixedDelayString = "${food.trade.seckill.order-request-recovery.fixed-delay-ms:60000}")
    public void recoverRequests() {
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
