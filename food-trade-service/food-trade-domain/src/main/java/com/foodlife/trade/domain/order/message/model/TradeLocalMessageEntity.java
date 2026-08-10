package com.foodlife.trade.domain.order.message.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class TradeLocalMessageEntity implements Serializable {

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
