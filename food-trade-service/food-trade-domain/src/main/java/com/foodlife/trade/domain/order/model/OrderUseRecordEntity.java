package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OrderUseRecordEntity implements Serializable {

    private Long id;
    private String useRecordNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private String tradeType;
    private String useSource;
    private String useStatus;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
