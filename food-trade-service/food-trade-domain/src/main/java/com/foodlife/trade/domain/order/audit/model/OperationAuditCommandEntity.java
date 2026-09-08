package com.foodlife.trade.domain.order.audit.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OperationAuditCommandEntity implements Serializable {

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
}
