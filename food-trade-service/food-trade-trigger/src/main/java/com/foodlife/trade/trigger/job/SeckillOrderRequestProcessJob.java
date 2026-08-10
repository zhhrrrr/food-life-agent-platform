package com.foodlife.trade.trigger.job;

import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestProcessResult;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "food.trade.seckill.order-request-process", name = "enabled", havingValue = "true")
public class SeckillOrderRequestProcessJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderRequestProcessJob.class);

    private final OrderDomainService orderDomainService;

    public SeckillOrderRequestProcessJob(OrderDomainService orderDomainService) {
        this.orderDomainService = orderDomainService;
    }

    @Scheduled(fixedDelayString = "${food.trade.seckill.order-request-process.fixed-delay-ms:30000}")
    public void processPendingRequests() {
        SeckillOrderRequestProcessResult result = orderDomainService.processPendingSeckillOrderRequests(null);
        if (result.getScannedCount() > 0) {
            LOGGER.info("seckill order request process completed, scannedCount={}, successCount={}, failedCount={}, retryCount={}",
                    result.getScannedCount(),
                    result.getSuccessCount(),
                    result.getFailedCount(),
                    result.getRetryCount());
        }
    }
}
