package com.foodlife.trade.domain.order.port;

import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;

public interface IBusinessPackagePort {

    PackageTradeSnapshot queryTradeSnapshot(Long packageId);
}
