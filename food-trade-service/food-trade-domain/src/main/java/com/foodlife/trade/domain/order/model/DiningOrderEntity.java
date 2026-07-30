package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DiningOrderEntity implements Serializable {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private Integer quantity;
    private Long totalAmount;
    private Long payAmount;
    private String tradeType;
    private String orderStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
