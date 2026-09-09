package com.foodlife.trade.domain.order.message.model;

import lombok.Data;

@Data
public class MqRepublishResult {

    private String messageId;
    private Boolean accepted;
    private String messageStatus;
    private String remark;
}
