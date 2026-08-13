package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NormalOrderTraceResponseDTO implements Serializable {

    private OrderInfo order;
    private PackageInfo packageInfo;
    private List<StockMessageInfo> stockMessages;
    private List<StockChangeRecordInfo> stockChangeRecords;
    private String currentStage;

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
    public static class StockMessageInfo implements Serializable {
        private Long id;
        private String messageId;
        private String messageType;
        private String bizType;
        private String bizId;
        private String messageStatus;
        private Integer retryCount;
        private Integer maxRetryCount;
        private LocalDateTime nextRetryTime;
        private String content;
        private String failReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class StockChangeRecordInfo implements Serializable {
        private Long id;
        private String operationId;
        private Long packageId;
        private Integer quantity;
        private String changeType;
        private String changeStatus;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
