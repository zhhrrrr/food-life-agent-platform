package com.foodlife.trade.domain.order.check;

import com.foodlife.trade.domain.order.model.OrderCreateContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderCreateCheckChain {

    private final List<OrderCreateCheckHandler> handlers;

    public OrderCreateCheckChain(List<OrderCreateCheckHandler> handlers) {
        this.handlers = handlers;
    }

    public void check(OrderCreateContext context, OrderCreateCheckStage stage) {
        for (OrderCreateCheckHandler handler : handlers) {
            if (handler.support(stage)) {
                handler.check(context);
            }
        }
    }
}
