package com.foodlife.trade.trigger.job;

import com.foodlife.trade.domain.order.normal.model.NormalOrderTimeoutCancelResult;
import com.foodlife.trade.domain.order.normal.service.NormalOrderTimeoutCancelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "food.trade.normal.order-timeout-cancel", name = "enabled", havingValue = "true")
public class NormalOrderTimeoutCancelJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(NormalOrderTimeoutCancelJob.class);

    private final NormalOrderTimeoutCancelService normalOrderTimeoutCancelService;

    @Value("${food.trade.normal.order-timeout-cancel.timeout-minutes:30}")
    private Integer timeoutMinutes;

    public NormalOrderTimeoutCancelJob(NormalOrderTimeoutCancelService normalOrderTimeoutCancelService) {
        this.normalOrderTimeoutCancelService = normalOrderTimeoutCancelService;
    }

    @Scheduled(fixedDelayString = "${food.trade.normal.order-timeout-cancel.fixed-delay-ms:60000}")
    public void cancelTimeoutOrders() {
        NormalOrderTimeoutCancelResult result = normalOrderTimeoutCancelService.cancelTimeoutOrders(timeoutMinutes, null);
        if (result.getScannedOrderCount() > 0 || result.getFailedOrderCount() > 0) {
            LOGGER.info("normal order timeout cancel completed, scannedOrderCount={}, canceledOrderCount={}, releaseStockMessageCount={}, failedOrderCount={}",
                    result.getScannedOrderCount(),
                    result.getCanceledOrderCount(),
                    result.getReleaseStockMessageCount(),
                    result.getFailedOrderCount());
        }
    }
}
