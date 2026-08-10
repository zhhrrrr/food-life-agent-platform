package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderRequestProcessResult implements Serializable {

    private Integer scannedCount = 0;
    private Integer successCount = 0;
    private Integer failedCount = 0;
    private Integer retryCount = 0;
}
