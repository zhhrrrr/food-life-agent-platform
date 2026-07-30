package com.foodlife.trade.domain.order.check;

import com.foodlife.trade.domain.order.model.OrderCreateContext;

public interface OrderCreateCheckHandler {

    boolean support(OrderCreateCheckStage stage);

    void check(OrderCreateContext context);
}
