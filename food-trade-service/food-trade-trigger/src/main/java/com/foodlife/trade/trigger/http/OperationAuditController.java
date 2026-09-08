package com.foodlife.trade.trigger.http;

import com.foodlife.trade.api.dto.OperationAuditLogListResponseDTO;
import com.foodlife.trade.api.dto.OperationAuditLogResponseDTO;
import com.foodlife.trade.domain.order.audit.model.OperationAuditLogEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditQueryEntity;
import com.foodlife.trade.domain.order.audit.service.OperationAuditDomainService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade/operations/audit-logs")
public class OperationAuditController {

    private final OperationAuditDomainService operationAuditDomainService;

    public OperationAuditController(OperationAuditDomainService operationAuditDomainService) {
        this.operationAuditDomainService = operationAuditDomainService;
    }

    @GetMapping
    public Response<OperationAuditLogListResponseDTO> listAuditLogs(@RequestParam(required = false) String traceId,
                                                                    @RequestParam(required = false) Long operatorId,
                                                                    @RequestParam(required = false) String operationType,
                                                                    @RequestParam(required = false) String bizType,
                                                                    @RequestParam(required = false) String bizId,
                                                                    @RequestParam(required = false) Integer limit) {
        OperationAuditQueryEntity query = new OperationAuditQueryEntity();
        query.setTraceId(traceId);
        query.setOperatorId(operatorId);
        query.setOperationType(operationType);
        query.setBizType(bizType);
        query.setBizId(bizId);
        query.setLimit(limit);
        return Response.success(toListResponse(operationAuditDomainService.list(query)));
    }

    private OperationAuditLogListResponseDTO toListResponse(List<OperationAuditLogEntity> logs) {
        OperationAuditLogListResponseDTO response = new OperationAuditLogListResponseDTO();
        response.setLogs(logs.stream().map(this::toResponse).collect(Collectors.toList()));
        return response;
    }

    private OperationAuditLogResponseDTO toResponse(OperationAuditLogEntity entity) {
        OperationAuditLogResponseDTO response = new OperationAuditLogResponseDTO();
        response.setId(entity.getId());
        response.setTraceId(entity.getTraceId());
        response.setOperatorId(entity.getOperatorId());
        response.setOperatorRole(entity.getOperatorRole());
        response.setOperationType(entity.getOperationType());
        response.setBizType(entity.getBizType());
        response.setBizId(entity.getBizId());
        response.setOperationStatus(entity.getOperationStatus());
        response.setRequestContent(entity.getRequestContent());
        response.setResponseContent(entity.getResponseContent());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }
}
