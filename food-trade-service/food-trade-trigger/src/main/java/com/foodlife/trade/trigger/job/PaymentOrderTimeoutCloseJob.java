package com.foodlife.trade.trigger.job;

import com.foodlife.job.lock.ScheduledJobLockExecutor;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderTimeoutCloseResult;
import com.foodlife.trade.domain.order.payment.service.PaymentOrderTimeoutCloseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "food.trade.payment.order-timeout-close", name = "enabled", havingValue = "true")
public class PaymentOrderTimeoutCloseJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentOrderTimeoutCloseJob.class);
    private static final String LOCK_KEY = "trade:payment-order-timeout-close";

    private final PaymentOrderTimeoutCloseService paymentOrderTimeoutCloseService;
    private final ScheduledJobLockExecutor jobLockExecutor;

    @Value("${food.trade.payment.order-timeout-close.timeout-minutes:30}")
    private Integer timeoutMinutes;

    public PaymentOrderTimeoutCloseJob(PaymentOrderTimeoutCloseService paymentOrderTimeoutCloseService,
                                       ScheduledJobLockExecutor jobLockExecutor) {
        this.paymentOrderTimeoutCloseService = paymentOrderTimeoutCloseService;
        this.jobLockExecutor = jobLockExecutor;
    }

    @Scheduled(fixedDelayString = "${food.trade.payment.order-timeout-close.fixed-delay-ms:60000}")
    public void closeTimeoutPreparedPaymentOrders() {
        jobLockExecutor.execute(LOCK_KEY, this::doCloseTimeoutPreparedPaymentOrders);
    }

    private void doCloseTimeoutPreparedPaymentOrders() {
        PaymentOrderTimeoutCloseResult result = paymentOrderTimeoutCloseService.closeTimeoutPreparedPaymentOrders(timeoutMinutes, null);
        if (result.getScannedPaymentCount() > 0 || result.getFailedPaymentCount() > 0) {
            LOGGER.info("payment order timeout close completed, scannedPaymentCount={}, closedPaymentCount={}, canceledOrderCount={}, failedPaymentCount={}",
                    result.getScannedPaymentCount(),
                    result.getClosedPaymentCount(),
                    result.getCanceledOrderCount(),
                    result.getFailedPaymentCount());
        }
    }
}
