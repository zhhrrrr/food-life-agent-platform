package com.foodlife.trade.domain.order.create;

import com.foodlife.trade.domain.order.check.OrderCreateCheckChain;
import com.foodlife.trade.domain.order.check.OrderCreateCheckStage;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.pricing.OrderPricingService;
import com.foodlife.trade.domain.order.repository.IOrderRepository;

public abstract class AbstractOrderCreateTemplate implements OrderCreateTemplate {

    private final OrderCreateCheckChain orderCreateCheckChain;
    private final OrderPricingService orderPricingService;
    private final OrderFactory orderFactory;
    private final IOrderRepository orderRepository;

    protected AbstractOrderCreateTemplate(OrderCreateCheckChain orderCreateCheckChain,
                                          OrderPricingService orderPricingService,
                                          OrderFactory orderFactory,
                                          IOrderRepository orderRepository) {
        this.orderCreateCheckChain = orderCreateCheckChain;
        this.orderPricingService = orderPricingService;
        this.orderFactory = orderFactory;
        this.orderRepository = orderRepository;
    }

    @Override
    public CreateOrderResult create(CreateOrderCommand command) {
        OrderCreateContext context = buildCreateContext(command);
        beforeCommandCheck(context);
        orderCreateCheckChain.check(context, OrderCreateCheckStage.COMMAND);

        PackageTradeSnapshot snapshot = loadPackageSnapshot(context);
        context.setPackageSnapshot(snapshot);

        beforeSnapshotCheck(context);
        orderCreateCheckChain.check(context, OrderCreateCheckStage.SNAPSHOT);

        beforePricing(context);
        OrderPricingResult pricingResult = orderPricingService.calculate(context);

        beforeCreateOrder(context, pricingResult);
        DiningOrderEntity order = orderFactory.createOrder(context.getTradeType(), command, snapshot, pricingResult);
        DiningOrderEntity savedOrder = orderRepository.saveOrder(order);

        DiningOrderItemEntity orderItem = orderFactory.createOrderItem(savedOrder, snapshot, command.getQuantity());
        orderRepository.saveOrderItem(orderItem);

        afterOrderSaved(context, savedOrder, orderItem);
        return buildResult(savedOrder);
    }

    protected OrderCreateContext buildCreateContext(CreateOrderCommand command) {
        OrderCreateContext context = new OrderCreateContext();
        context.setTradeType(getTradeType());
        context.setCommand(command);
        return context;
    }

    protected abstract String getTradeType();

    protected abstract PackageTradeSnapshot loadPackageSnapshot(OrderCreateContext context);

    protected void beforeCommandCheck(OrderCreateContext context) {
    }

    protected void beforeSnapshotCheck(OrderCreateContext context) {
    }

    protected void beforePricing(OrderCreateContext context) {
    }

    protected void beforeCreateOrder(OrderCreateContext context, OrderPricingResult pricingResult) {
    }

    protected void afterOrderSaved(OrderCreateContext context, DiningOrderEntity order, DiningOrderItemEntity orderItem) {
    }

    private CreateOrderResult buildResult(DiningOrderEntity savedOrder) {
        CreateOrderResult result = new CreateOrderResult();
        result.setOrderId(savedOrder.getId());
        result.setOrderNo(savedOrder.getOrderNo());
        result.setPayAmount(savedOrder.getPayAmount());
        result.setOrderStatus(savedOrder.getOrderStatus());
        return result;
    }
}
