package com.foodlife.trade.trigger.job;

import com.foodlife.job.lock.ScheduledJobLockExecutor;
import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.payment.service.ExternalPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "food.payment.alipay", name = "enabled", havingValue = "true")
public class AlipayPaymentSyncJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlipayPaymentSyncJob.class);
    private final IPaymentOrderRepository repository;
    private final ExternalPaymentService payments;
    private final ScheduledJobLockExecutor locks;
    public AlipayPaymentSyncJob(IPaymentOrderRepository repository, ExternalPaymentService payments,
                               ScheduledJobLockExecutor locks) {
        this.repository = repository;
        this.payments = payments;
        this.locks = locks;
    }

    @Scheduled(fixedDelayString = "${food.payment.alipay.sync-delay-ms:30000}")
    public void syncPendingPayments() {
        locks.execute("trade:alipay-sync", () -> {
            for (PaymentOrderEntity payment : repository.listRecentPaymentOrders(100)) {
                if (!PaymentChannelConstants.ALIPAY_SANDBOX.equals(payment.getChannel())
                        || !("PREPARED".equals(payment.getPayStatus()) || "CLOSED".equals(payment.getPayStatus()))
                        || payment.getCreateTime().isBefore(LocalDateTime.now().minusHours(24))) { continue; }
                try {
                    PaymentCallbackCommand paid = payments.queryPaidPayment(payment);
                    if (paid != null) { payments.acceptPaidPayment(paid); }
                } catch (Exception e) {
                    LOGGER.warn("Alipay payment sync failed, payOrderNo={}, reason={}", payment.getPayOrderNo(), e.getMessage());
                }
            }
        });
    }
}
