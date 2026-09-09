package com.foodlife.trade.domain.order.message.model;

import lombok.Data;

@Data
public class OperationMqMessageQuery {

    private String messageStatus;
    private String messageType;
    private String bizId;
    private Integer limit;
}
