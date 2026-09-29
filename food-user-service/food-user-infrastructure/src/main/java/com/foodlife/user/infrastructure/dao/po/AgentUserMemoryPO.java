package com.foodlife.user.infrastructure.dao.po;

import lombok.Data;

@Data
public class AgentUserMemoryPO {
    private Long id;
    private Long userId;
    private String memoryType;
    private String memoryKey;
    private String valueJson;
    private String sourceTurnId;
    private Long version;
    private Integer status;
}
