package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderRequestRecoveryResult implements Serializable {

    private Integer scannedMessageCount = 0;
    private Integer recoveredMessageCount = 0;
    private Integer canceledRequestCount = 0;
    private Integer releasedStockCount = 0;
}
