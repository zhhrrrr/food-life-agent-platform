package com.foodlife.trade.domain.order.check.handler;

import com.foodlife.trade.domain.order.check.OrderCreateCheckHandler;
import com.foodlife.trade.domain.order.constant.OrderPatternGroups;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import org.springframework.stereotype.Component;

@Component
public class PackageTradeCheckHandler implements OrderCreateCheckHandler {

    @Override
    public String group() {
        return OrderPatternGroups.CREATE_SNAPSHOT_CHECK;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void handle(OrderCreateContext context) {
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
