package com.foodlife.trade.domain.order.normal.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NormalPackageStockSyncResult implements Serializable {

    private LocalDateTime compensateTime;
    private Integer scannedMessageCount = 0;
    private Integer successCount = 0;
    private Integer retryCount = 0;
    private Integer failedCount = 0;
}
