package com.foodlife.trade.domain.order.coupon.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CouponExpireScanResult implements Serializable {

    private LocalDateTime scanTime;
    private LocalDateTime expireBefore;
    private Integer expiredCount;
    private Integer limit;
}
