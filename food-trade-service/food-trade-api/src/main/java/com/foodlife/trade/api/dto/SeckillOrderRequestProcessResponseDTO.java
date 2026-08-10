package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderRequestProcessResponseDTO implements Serializable {

    private Integer scannedCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer retryCount;
}
