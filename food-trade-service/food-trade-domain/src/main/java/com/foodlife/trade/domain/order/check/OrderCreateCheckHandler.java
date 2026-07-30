package com.foodlife.trade.domain.order.check;

import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.patterns.chain.BusinessChainHandler;

public interface OrderCreateCheckHandler extends BusinessChainHandler<OrderCreateContext> {
}
