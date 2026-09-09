package com.foodlife.trade.domain.order.message.model;

import lombok.Data;

@Data
public class MqQueueBacklogEntity {

    private String queueName;
    private Integer messageCount;
    private Integer consumerCount;
    private Integer backlogThreshold;
    private Boolean backlogAlarm;
}
