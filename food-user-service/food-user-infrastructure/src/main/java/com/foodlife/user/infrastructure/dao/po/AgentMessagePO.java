package com.foodlife.user.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentMessagePO {
    private Long id;
    private String sessionId;
    private Long userId;
    private String turnId;
    private Long sequenceNo;
    private String role;
    private String content;
    private String intent;
    private String runId;
    private LocalDateTime createTime;
}
