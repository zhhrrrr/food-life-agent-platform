package com.foodlife.trade.domain.order.create;

import com.foodlife.trade.domain.order.check.OrderCreateCheckChain;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.pricing.OrderPricingService;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Component;

@Component
public class NormalOrderCreateTemplate extends AbstractOrderCreateTemplate {

    private final IBusinessPackagePort businessPackagePort;

    public NormalOrderCreateTemplate(OrderCreateCheckChain orderCreateCheckChain,
                                     OrderPricingService orderPricingService,
                                     OrderFactory orderFactory,
                                     IOrderRepository orderRepository,
                                     IBusinessPackagePort businessPackagePort) {
        super(orderCreateCheckChain, orderPricingService, orderFactory, orderRepository);
        this.businessPackagePort = businessPackagePort;
    }

    @Override
    public boolean support(String tradeType) {
        return TradeTypeConstants.NORMAL.equals(tradeType);
    }

    @Override
    protected String getTradeType() {
        return TradeTypeConstants.NORMAL;
    }

    @Override
    protected PackageTradeSnapshot loadPackageSnapshot(OrderCreateContext context) {
        return businessPackagePort.queryTradeSnapshot(context.getCommand().getPackageId());
    }
}
