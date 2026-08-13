package com.foodlife.trade.trigger.job;

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

    private final CouponService couponService;

    @Value("${food.trade.coupon.expire-scan.limit:500}")
    private Integer limit;

    public CouponExpireScanJob(CouponService couponService) {
        this.couponService = couponService;
    }

    @Scheduled(fixedDelayString = "${food.trade.coupon.expire-scan.fixed-delay-ms:60000}")
    public void scanExpiredCoupons() {
        CouponExpireScanResult result = couponService.expireUnusedCoupons(limit);
        if (result.getExpiredCount() > 0) {
            LOGGER.info("coupon expire scan completed, expiredCount={}, limit={}",
                    result.getExpiredCount(),
                    result.getLimit());
        }
    }
}
