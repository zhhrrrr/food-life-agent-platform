package com.foodlife.trade.domain.order.check.handler;

import com.foodlife.trade.domain.order.check.OrderCreateCheckHandler;
import com.foodlife.trade.domain.order.check.OrderCreateCheckStage;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(20)
@Component
public class PackageTradeCheckHandler implements OrderCreateCheckHandler {

    @Override
    public boolean support(OrderCreateCheckStage stage) {
        return OrderCreateCheckStage.SNAPSHOT == stage;
    }

    @Override
    public void check(OrderCreateContext context) {
        PackageTradeSnapshot snapshot = context == null ? null : context.getPackageSnapshot();
        if (snapshot == null) {
            throw new IllegalArgumentException("package not found");
        }
        if (snapshot.getPackageStatus() == null || snapshot.getPackageStatus() != 1) {
            throw new IllegalArgumentException("package offline");
        }
        if (snapshot.getStock() == null || snapshot.getStock() < context.getCommand().getQuantity()) {
            throw new IllegalArgumentException("package stock not enough");
        }
    }
}
