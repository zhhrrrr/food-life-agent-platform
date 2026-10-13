package com.foodlife.trade.trigger.job;

import com.foodlife.job.lock.ScheduledJobLockExecutor;
import com.foodlife.trade.domain.order.normal.model.NormalPackageStockSyncResult;
import com.foodlife.trade.domain.order.normal.service.NormalPackageStockMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "food.trade.normal.package-stock-sync", name = "enabled", havingValue = "true")
public class NormalPackageStockSyncJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(NormalPackageStockSyncJob.class);
    private static final String LOCK_KEY = "trade:normal-package-stock-sync";

    private final NormalPackageStockMessageService normalPackageStockMessageService;
    private final ScheduledJobLockExecutor jobLockExecutor;

    public NormalPackageStockSyncJob(NormalPackageStockMessageService normalPackageStockMessageService,
                                     ScheduledJobLockExecutor jobLockExecutor) {
        this.normalPackageStockMessageService = normalPackageStockMessageService;
        this.jobLockExecutor = jobLockExecutor;
    }

    @Scheduled(fixedDelayString = "${food.trade.normal.package-stock-sync.fixed-delay-ms:60000}")
    public void compensatePackageStockMessages() {
        jobLockExecutor.execute(LOCK_KEY, this::doCompensatePackageStockMessages);
    }

    private void doCompensatePackageStockMessages() {
        NormalPackageStockSyncResult result = normalPackageStockMessageService.compensatePendingMessages(null);
        if (result.getScannedMessageCount() > 0 || result.getRetryCount() > 0 || result.getFailedCount() > 0) {
            LOGGER.info("normal package stock sync completed, scannedMessageCount={}, successCount={}, retryCount={}, failedCount={}",
                    result.getScannedMessageCount(),
                    result.getSuccessCount(),
                    result.getRetryCount(),
                    result.getFailedCount());
        }
    }
}
