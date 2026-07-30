package com.foodlife.trade.domain.order.check.handler;

import com.foodlife.trade.domain.order.check.OrderCreateCheckHandler;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import org.springframework.stereotype.Component;

@Component
public class PackageStatusCheckHandler implements OrderCreateCheckHandler {

    @Override
    public Void apply(OrderCreateContext context, OrderCreateContext dynamicContext) {
        PackageTradeSnapshot snapshot = dynamicContext == null ? null : dynamicContext.getPackageSnapshot();
        if (snapshot == null) {
            throw new IllegalArgumentException("package not found");
        }
        if (snapshot.getPackageStatus() == null || snapshot.getPackageStatus() != 1) {
            throw new IllegalArgumentException("package offline");
        }
        return next(context, dynamicContext);
    }
}
