package com.foodlife.user.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentSessionPO {
    private Long id;
    private String sessionId;
    private Long userId;
    private String summary;
    private Long summaryThroughSeq;
    private Long version;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
