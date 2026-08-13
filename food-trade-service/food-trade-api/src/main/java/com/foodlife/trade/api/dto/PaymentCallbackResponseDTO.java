package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentCallbackResponseDTO implements Serializable {

    private String callbackBehavior;
    private PaymentOrderResponseDTO paymentOrder;
    private PayOrderResponseDTO settlement;
}
