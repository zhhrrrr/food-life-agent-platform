package com.foodlife.user.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserMemoryLocalMessagePO {
    private Long id;
    private String messageId;
    private String eventType;
    private String eventId;
    private String content;
    private String messageStatus;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextRetryTime;
    private String failReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
