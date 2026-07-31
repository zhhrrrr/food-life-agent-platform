package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDetailResponseDTO implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private Integer quantity;
    private Long totalAmount;
    private Long payAmount;
    private String tradeType;
    private String orderStatus;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
    private List<OrderItemResponseDTO> items;
}
