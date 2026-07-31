package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderListResponseDTO implements Serializable {

    private List<OrderInfo> orders;
    private Boolean hasMore;
    private Long lastId;

    @Data
    public static class OrderInfo implements Serializable {

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
        private Long payAmount;
        private String tradeType;
        private String orderStatus;
        private LocalDateTime useTime;
        private LocalDateTime createTime;
    }
}
