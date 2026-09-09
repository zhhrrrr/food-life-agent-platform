package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PaymentReconcileResponseDTO implements Serializable {

    private Integer scannedPaymentCount;
    private Integer preparedPaymentCount;
    private Integer successPaymentCount;
    private Integer closedPaymentCount;
    private Integer refundedPaymentCount;
    private Integer scannedRefundCount;
    private Integer preparedRefundCount;
    private Integer successRefundCount;
    private Integer failedRefundCount;
    private List<String> inconsistentPayOrderNos = new ArrayList<>();
}

