package com.foodlife.trade.trigger.app;

import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustResult;
import com.foodlife.trade.domain.order.distributedtx.service.DistributedTxDemoService;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;

@Service
public class SeataDemoApplicationService {

    private final DistributedTxDemoService distributedTxDemoService;

    public SeataDemoApplicationService(DistributedTxDemoService distributedTxDemoService) {
        this.distributedTxDemoService = distributedTxDemoService;
    }

    @GlobalTransactional(name = "food-seata-package-stock-adjust", rollbackFor = Exception.class)
    public DistributedPackageStockAdjustResult adjustPackageStock(DistributedPackageStockAdjustCommand command) {
        return distributedTxDemoService.adjustPackageStock(command);
    }
}
