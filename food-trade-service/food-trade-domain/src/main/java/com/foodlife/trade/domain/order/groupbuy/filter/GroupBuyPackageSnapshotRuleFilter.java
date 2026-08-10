package com.foodlife.trade.domain.order.groupbuy.filter;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyPackageSnapshotRuleFilter implements GroupBuyLockRuleFilter {

    private final IBusinessPackagePort businessPackagePort;

    public GroupBuyPackageSnapshotRuleFilter(IBusinessPackagePort businessPackagePort) {
        this.businessPackagePort = businessPackagePort;
    }

    @Override
    public Void apply(GroupBuyLockContext requestParameter, GroupBuyLockContext dynamicContext) {
        PackageTradeSnapshot snapshot = businessPackagePort.queryTradeSnapshot(requestParameter.getCommand().getPackageId());
        if (snapshot == null) {
            throw new IllegalArgumentException("package not found");
        }
        if (snapshot.getPackageStatus() == null || snapshot.getPackageStatus() != 1) {
            throw new IllegalArgumentException("package not available");
        }
        if (snapshot.getStock() == null || snapshot.getStock() < requestParameter.getCommand().getQuantity()) {
            throw new IllegalArgumentException("package stock not enough");
        }
        dynamicContext.setPackageSnapshot(snapshot);
        return next(requestParameter, dynamicContext);
    }
}
