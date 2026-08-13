package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NormalPackageStockSyncResponseDTO implements Serializable {

    private LocalDateTime compensateTime;
    private Integer scannedMessageCount;
    private Integer successCount;
    private Integer retryCount;
    private Integer failedCount;
}
