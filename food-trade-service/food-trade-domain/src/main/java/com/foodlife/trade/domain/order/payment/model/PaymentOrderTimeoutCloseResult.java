package com.foodlife.trade.domain.order.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PaymentOrderTimeoutCloseResult implements Serializable {

    private LocalDateTime compensateTime;
    private Integer timeoutMinutes;
    private LocalDateTime timeoutBefore;
    private Integer scannedPaymentCount;
    private Integer closedPaymentCount;
    private Integer canceledOrderCount;
    private Integer failedPaymentCount;
    private List<PaymentOrderTimeoutCloseDetail> details = new ArrayList<>();
}
