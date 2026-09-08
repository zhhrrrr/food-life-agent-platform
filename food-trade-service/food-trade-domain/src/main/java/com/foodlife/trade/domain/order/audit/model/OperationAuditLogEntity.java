package com.foodlife.trade.domain.order.audit.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OperationAuditLogEntity implements Serializable {

    private Long id;
    private String traceId;
    private Long operatorId;
    private String operatorRole;
    private String operationType;
    private String bizType;
    private String bizId;
    private String operationStatus;
    private String requestContent;
    private String responseContent;
    private String remark;
    private LocalDateTime createTime;
}
