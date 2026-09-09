package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OperationMqQueueResponseDTO implements Serializable {

    private String queueName;
    private Integer messageCount;
    private Integer consumerCount;
    private Integer backlogThreshold;
    private Boolean backlogAlarm;
}
