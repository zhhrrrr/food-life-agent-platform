package com.foodlife.user.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentMemoryEventPO {
    private Long id;
    private String eventId;
    private Long userId;
    private String eventType;
    private String content;
    private String entityType;
    private String entityId;
    private String sourceTurnId;
    private String status;
    private LocalDateTime occurredAt;
    private LocalDateTime expiresAt;
}
