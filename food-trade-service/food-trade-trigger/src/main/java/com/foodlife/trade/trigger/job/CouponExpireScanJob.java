package com.foodlife.trade.trigger.job;

import com.foodlife.job.lock.ScheduledJobLockExecutor;
import com.foodlife.trade.domain.order.coupon.model.CouponExpireScanResult;
import com.foodlife.trade.domain.order.coupon.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "food.trade.coupon.expire-scan", name = "enabled", havingValue = "true")
public class CouponExpireScanJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(CouponExpireScanJob.class);
    private static final String LOCK_KEY = "trade:coupon-expire-scan";

    private final CouponService couponService;
    private final ScheduledJobLockExecutor jobLockExecutor;

    @Value("${food.trade.coupon.expire-scan.limit:500}")
    private Integer limit;

    public CouponExpireScanJob(CouponService couponService,
                               ScheduledJobLockExecutor jobLockExecutor) {
        this.couponService = couponService;
        this.jobLockExecutor = jobLockExecutor;
    }

    @Scheduled(fixedDelayString = "${food.trade.coupon.expire-scan.fixed-delay-ms:60000}")
    public void scanExpiredCoupons() {
        jobLockExecutor.execute(LOCK_KEY, this::doScanExpiredCoupons);
    }

    private void doScanExpiredCoupons() {
        CouponExpireScanResult result = couponService.expireUnusedCoupons(limit);
        if (result.getExpiredCount() > 0) {
            LOGGER.info("coupon expire scan completed, expiredCount={}, limit={}",
                    result.getExpiredCount(),
                    result.getLimit());
        }
    }
}
