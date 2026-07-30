package com.foodlife.trade.domain.order.check;

import com.foodlife.trade.domain.order.check.handler.OrderCommandCheckHandler;
import com.foodlife.trade.domain.order.check.handler.PackageTradeCheckHandler;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.patterns.framework.link.model2.LinkArmory;
import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import org.springframework.stereotype.Component;

@Component
public class OrderCreateCheckChain {

    private final BusinessLinkedList<OrderCreateContext, OrderCreateContext, Void> commandCheckLink;
    private final BusinessLinkedList<OrderCreateContext, OrderCreateContext, Void> snapshotCheckLink;

    public OrderCreateCheckChain(OrderCommandCheckHandler orderCommandCheckHandler,
                                 PackageTradeCheckHandler packageTradeCheckHandler) {
        this.commandCheckLink = new LinkArmory<>("订单创建-命令参数校验链", orderCommandCheckHandler).getLogicLink();
        this.snapshotCheckLink = new LinkArmory<>("订单创建-套餐快照校验链", packageTradeCheckHandler).getLogicLink();
    }

    public void check(OrderCreateContext context, OrderCreateCheckStage stage) throws Exception {
        if (OrderCreateCheckStage.COMMAND == stage) {
            commandCheckLink.apply(context, context);
            return;
        }
        if (OrderCreateCheckStage.SNAPSHOT == stage) {
            snapshotCheckLink.apply(context, context);
        }
    }
}
