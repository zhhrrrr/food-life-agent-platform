package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OrderSummaryEntity implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private String shopNameSnapshot;
    private Long packageId;
    private String packageNameSnapshot;
    private String coverImageSnapshot;
    private Integer quantity;
    private Long totalAmount;
    private Long discountAmount;
    private Long payAmount;
    private Long userCouponId;
    private String tradeType;
    private String orderStatus;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
}
