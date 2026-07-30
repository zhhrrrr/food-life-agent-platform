package com.foodlife.trade.domain.order.create;

import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;

public interface OrderCreateTemplate {

    boolean support(String tradeType);

    CreateOrderResult create(CreateOrderCommand command);
}
