package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderRequestRecoveryResponseDTO implements Serializable {

    private Integer scannedMessageCount;
    private Integer recoveredMessageCount;
    private Integer canceledRequestCount;
    private Integer releasedStockCount;
}
