package com.foodlife.trade.domain.order.check;

import com.foodlife.trade.domain.order.check.handler.OrderCommandCheckHandler;
import com.foodlife.trade.domain.order.check.handler.PackageSnapshotLoadHandler;
import com.foodlife.trade.domain.order.check.handler.PackageStatusCheckHandler;
import com.foodlife.trade.domain.order.check.handler.PackageStockCheckHandler;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.patterns.framework.link.model2.LinkArmory;
import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import org.springframework.stereotype.Component;

@Component
public class OrderCreateCheckChain {

    private final BusinessLinkedList<OrderCreateContext, OrderCreateContext, Void> normalOrderCreateCheckLink;

    public OrderCreateCheckChain(OrderCommandCheckHandler orderCommandCheckHandler,
                                 PackageSnapshotLoadHandler packageSnapshotLoadHandler,
                                 PackageStatusCheckHandler packageStatusCheckHandler,
                                 PackageStockCheckHandler packageStockCheckHandler) {
        this.normalOrderCreateCheckLink = new LinkArmory<>(
                "普通购买下单规则过滤链",
                orderCommandCheckHandler,
                packageSnapshotLoadHandler,
                packageStatusCheckHandler,
                packageStockCheckHandler
        ).getLogicLink();
    }

    public void checkNormalOrder(OrderCreateContext context) throws Exception {
        normalOrderCreateCheckLink.apply(context, context);
    }
}
