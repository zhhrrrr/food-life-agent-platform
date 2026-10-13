package com.foodlife.trade.trigger.job;

import com.foodlife.job.lock.ScheduledJobLockExecutor;
import com.foodlife.trade.domain.order.payment.service.RefundRecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefundRecoveryJob {
    private final RefundRecoveryService refunds;
    private final ScheduledJobLockExecutor locks;

    public RefundRecoveryJob(RefundRecoveryService refunds, ScheduledJobLockExecutor locks) {
        this.refunds = refunds;
        this.locks = locks;
    }

    @Scheduled(fixedDelayString = "${food.payment.refund.recovery-delay-ms:60000}")
    public void recover() {
        locks.execute("trade:refund-recovery", () -> refunds.recover(50));
    }
}
