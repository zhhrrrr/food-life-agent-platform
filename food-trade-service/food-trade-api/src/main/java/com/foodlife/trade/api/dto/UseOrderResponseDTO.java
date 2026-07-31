package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UseOrderResponseDTO implements Serializable {

    private Long userId;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private String useBehavior;
    private LocalDateTime useTime;
}
