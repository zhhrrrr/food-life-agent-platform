package com.foodlife.trade.domain.order.payment.model;

import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentCallbackResult implements Serializable {

    private PaymentOrderEntity paymentOrder;
    private OrderPaySettlementEntity settlement;
    private String callbackBehavior;
}
