package com.foodlife.trade.domain.order.check;

import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.patterns.chain.BusinessChainRouter;
import org.springframework.stereotype.Component;

@Component
public class OrderCreateCheckChain {

    private final BusinessChainRouter businessChainRouter;

    public OrderCreateCheckChain(BusinessChainRouter businessChainRouter) {
        this.businessChainRouter = businessChainRouter;
    }

    public void check(OrderCreateContext context, OrderCreateCheckStage stage) {
        businessChainRouter.execute(stage.group(), context);
    }
}
