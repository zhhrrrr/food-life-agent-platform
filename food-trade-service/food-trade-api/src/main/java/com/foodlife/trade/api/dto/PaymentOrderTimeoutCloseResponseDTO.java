package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PaymentOrderTimeoutCloseResponseDTO implements Serializable {

    private LocalDateTime compensateTime;
    private Integer timeoutMinutes;
    private LocalDateTime timeoutBefore;
    private Integer scannedPaymentCount;
    private Integer closedPaymentCount;
    private Integer canceledOrderCount;
    private Integer failedPaymentCount;
    private List<Detail> details = new ArrayList<>();

    @Data
    public static class Detail implements Serializable {
        private String payOrderNo;
        private Long orderId;
        private String orderNo;
        private Long userId;
        private Long payAmount;
        private String beforePayStatus;
        private String afterPayStatus;
        private Boolean paymentClosed;
        private String beforeOrderStatus;
        private String afterOrderStatus;
        private Boolean orderCanceled;
        private String failReason;
    }
}
