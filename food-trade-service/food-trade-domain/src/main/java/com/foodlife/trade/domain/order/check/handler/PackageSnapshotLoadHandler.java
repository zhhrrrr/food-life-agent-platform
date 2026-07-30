package com.foodlife.trade.domain.order.check.handler;

import com.foodlife.trade.domain.order.check.OrderCreateCheckHandler;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import org.springframework.stereotype.Component;

@Component
public class PackageSnapshotLoadHandler implements OrderCreateCheckHandler {

    private final IBusinessPackagePort businessPackagePort;

    public PackageSnapshotLoadHandler(IBusinessPackagePort businessPackagePort) {
        this.businessPackagePort = businessPackagePort;
    }

    @Override
    public Void apply(OrderCreateContext context, OrderCreateContext dynamicContext) {
        PackageTradeSnapshot snapshot = businessPackagePort.queryTradeSnapshot(context.getCommand().getPackageId());
        dynamicContext.setPackageSnapshot(snapshot);
        return next(context, dynamicContext);
    }
}
