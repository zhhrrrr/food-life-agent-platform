package com.foodlife.trade.domain.order.check.handler;

import com.foodlife.trade.domain.order.check.OrderCreateCheckHandler;
import com.foodlife.trade.domain.order.constant.OrderPatternGroups;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import org.springframework.stereotype.Component;

@Component
public class OrderCommandCheckHandler implements OrderCreateCheckHandler {

    @Override
    public String group() {
        return OrderPatternGroups.CREATE_COMMAND_CHECK;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void handle(OrderCreateContext context) {
        CreateOrderCommand command = context == null ? null : context.getCommand();
        if (command == null || command.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (command.getPackageId() == null) {
            throw new IllegalArgumentException("packageId required");
        }
        if (command.getQuantity() == null || command.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity invalid");
        }
    }
}
