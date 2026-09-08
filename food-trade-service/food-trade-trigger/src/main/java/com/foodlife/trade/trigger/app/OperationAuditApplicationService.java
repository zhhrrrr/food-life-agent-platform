package com.foodlife.trade.trigger.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.auth.context.UserHolder;
import com.foodlife.auth.model.LoginUserDTO;
import com.foodlife.trade.domain.order.audit.model.OperationAuditCommandEntity;
import com.foodlife.trade.domain.order.audit.service.OperationAuditDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationAuditApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditApplicationService.class);
    private static final int MAX_CONTENT_LENGTH = 4000;

    private final OperationAuditDomainService operationAuditDomainService;
    private final ObjectMapper objectMapper;

    public OperationAuditApplicationService(OperationAuditDomainService operationAuditDomainService,
                                            ObjectMapper objectMapper) {
        this.operationAuditDomainService = operationAuditDomainService;
        this.objectMapper = objectMapper;
    }

    public void recordSuccess(String operationType, String bizType, String bizId,
                              Object request, Object response, String remark) {
        OperationAuditCommandEntity command = baseCommand(operationType, bizType, bizId, remark);
        command.setOperationStatus("SUCCESS");
        command.setRequestContent(toJson(request));
        command.setResponseContent(toJson(response));
        recordSafely(command);
    }

    private void recordSafely(OperationAuditCommandEntity command) {
        try {
            operationAuditDomainService.record(command);
        } catch (Exception e) {
            log.warn("operation audit record failed, operationType={}, bizType={}, bizId={}",
                    command == null ? null : command.getOperationType(),
                    command == null ? null : command.getBizType(),
                    command == null ? null : command.getBizId(),
                    e);
        }
    }

    private OperationAuditCommandEntity baseCommand(String operationType, String bizType, String bizId, String remark) {
        LoginUserDTO user = UserHolder.getUser();
        OperationAuditCommandEntity command = new OperationAuditCommandEntity();
        command.setTraceId(readTraceId());
        command.setOperatorId(user == null ? null : user.getId());
        command.setOperatorRole(user == null || !StringUtils.hasText(user.getRole()) ? "UNKNOWN" : user.getRole());
        command.setOperationType(operationType);
        command.setBizType(bizType);
        command.setBizId(bizId);
        command.setRemark(remark);
        return command;
    }

    private String readTraceId() {
        String traceId = MDC.get("traceId");
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        traceId = MDC.get("trace-id");
        return StringUtils.hasText(traceId) ? traceId : null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            return truncate(String.valueOf(value));
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_CONTENT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CONTENT_LENGTH);
    }
}
