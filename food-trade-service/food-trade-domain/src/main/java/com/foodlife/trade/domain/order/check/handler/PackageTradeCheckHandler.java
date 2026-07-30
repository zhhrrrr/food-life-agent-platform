package com.foodlife.trade.domain.order.check.handler;

import com.foodlife.trade.domain.order.check.OrderCreateCheckHandler;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import org.springframework.stereotype.Component;

@Component
public class PackageTradeCheckHandler implements OrderCreateCheckHandler {

    @Override
    public Void apply(OrderCreateContext context, OrderCreateContext dynamicContext) {
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
        return next(context, dynamicContext);
    }
}
