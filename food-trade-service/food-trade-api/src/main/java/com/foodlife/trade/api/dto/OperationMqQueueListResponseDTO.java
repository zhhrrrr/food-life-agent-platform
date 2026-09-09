package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OperationMqQueueListResponseDTO implements Serializable {

    private List<OperationMqQueueResponseDTO> queues;
}
