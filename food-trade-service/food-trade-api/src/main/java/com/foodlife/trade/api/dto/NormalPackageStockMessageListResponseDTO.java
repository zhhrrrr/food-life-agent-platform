package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NormalPackageStockMessageListResponseDTO implements Serializable {

    private List<MessageInfo> messages;

    @Data
    public static class MessageInfo implements Serializable {
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
}
