package com.foodlife.trade.domain.order.audit.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OperationAuditQueryEntity implements Serializable {

    private String traceId;
    private Long operatorId;
    private String operationType;
    private String bizType;
    private String bizId;
    private Integer limit;
}
