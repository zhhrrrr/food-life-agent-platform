package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SeckillOrderTraceResponseDTO implements Serializable {

    private RequestInfo request;
    private OrderInfo order;
    private ActivityInfo activity;
    private PackageInfo packageInfo;
    private StockInfo stock;
    private String currentStage;

    @Data
    public static class RequestInfo implements Serializable {
        private String requestNo;
        private Long userId;
        private Long activityId;
        private Long packageId;
        private Integer quantity;
        private Long orderId;
        private String orderNo;
        private String requestStatus;
        private String failReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class OrderInfo implements Serializable {
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

    @Data
    public static class ActivityInfo implements Serializable {
        private Long activityId;
        private Long packageId;
        private String activityName;
        private Long seckillPrice;
        private Integer activityStatus;
        private LocalDateTime validStartTime;
        private LocalDateTime validEndTime;
        private Integer userTakeLimit;
    }

    @Data
    public static class PackageInfo implements Serializable {
        private Long shopId;
        private String shopName;
        private Long packageId;
        private String packageName;
        private String packageDescription;
        private String coverImage;
        private Long price;
        private Long originalPrice;
        private Integer stock;
        private Integer packageStatus;
        private String useRule;
    }

    @Data
    public static class StockInfo implements Serializable {
        private Integer dbStock;
        private Integer redisStock;
        private Integer waitPayCount;
        private Integer paidCount;
    }
}
