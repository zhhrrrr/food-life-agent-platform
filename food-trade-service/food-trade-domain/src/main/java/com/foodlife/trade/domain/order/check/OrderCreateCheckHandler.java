package com.foodlife.trade.domain.order.check;

import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;

public interface OrderCreateCheckHandler extends ILogicHandler<OrderCreateContext, OrderCreateContext, Void> {
}
