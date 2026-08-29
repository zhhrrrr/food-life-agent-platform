package com.foodlife.business.domain.review.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class TradeOrderForReviewEntity implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private Integer quantity;
    private String tradeType;
    private String orderStatus;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
}
