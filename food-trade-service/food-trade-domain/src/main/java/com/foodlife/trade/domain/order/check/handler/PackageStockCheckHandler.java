package com.foodlife.trade.domain.order.check.handler;

import com.foodlife.trade.domain.order.check.OrderCreateCheckHandler;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import org.springframework.stereotype.Component;

@Component
public class PackageStockCheckHandler implements OrderCreateCheckHandler {

    @Override
    public Void apply(OrderCreateContext context, OrderCreateContext dynamicContext) {
        PackageTradeSnapshot snapshot = dynamicContext == null ? null : dynamicContext.getPackageSnapshot();
        if (snapshot == null) {
            throw new IllegalArgumentException("package not found");
        }
        if (snapshot.getStock() == null || snapshot.getStock() < context.getCommand().getQuantity()) {
            throw new IllegalArgumentException("package stock not enough");
        }
        return next(context, dynamicContext);
    }
}
