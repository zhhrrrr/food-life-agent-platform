package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OperationAuditLogListResponseDTO implements Serializable {

    private List<OperationAuditLogResponseDTO> logs;
}
