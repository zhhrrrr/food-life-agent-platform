package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserGroupBuyOrderListResponseDTO implements Serializable {

    private List<OrderInfo> orders = new ArrayList<>();
    private Boolean hasMore;
    private Long lastId;

    @Data
    public static class OrderInfo implements Serializable {
        private Long groupBuyOrderListId;
        private Long userId;
        private String teamId;
        private Long orderId;
        private String orderNo;
        private Long activityId;
        private Long packageId;
        private Long payAmount;
        private String groupBuyOrderStatus;
        private String orderStatus;
        private String teamStatus;
        private Integer targetCount;
        private Integer lockCount;
        private Integer completeCount;
        private Integer remainingCount;
        private LocalDateTime outTradeTime;
        private LocalDateTime validEndTime;
        private LocalDateTime createTime;
        private Boolean canPay;
        private Boolean canCancel;
        private Boolean canRefund;
    }
}
