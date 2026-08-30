package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UseOrderResponseDTO implements Serializable {

    private Long userId;
    private Long orderId;
    private String orderNo;
    private Long shopId;
    private Long packageId;
    private String tradeType;
    private String orderStatus;
    private String useBehavior;
    private Long useRecordId;
    private String useRecordNo;
    private LocalDateTime useTime;
}
