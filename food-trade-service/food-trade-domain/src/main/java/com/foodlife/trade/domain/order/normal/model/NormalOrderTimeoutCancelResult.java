package com.foodlife.trade.domain.order.normal.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NormalOrderTimeoutCancelResult implements Serializable {

    private LocalDateTime compensateTime;
    private Integer timeoutMinutes;
    private LocalDateTime timeoutBefore;
    private Integer scannedOrderCount;
    private Integer canceledOrderCount;
    private Integer releaseStockMessageCount;
    private Integer failedOrderCount;
    private List<NormalOrderTimeoutCancelDetail> details = new ArrayList<>();
}
