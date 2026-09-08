package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("trade_operation_audit_log")
public class OperationAuditLogPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
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
