package com.foodlife.trade.domain.order.service;

import com.foodlife.trade.domain.order.check.OrderCreateCheckChain;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderDetailEntity;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.pricing.OrderPricingService;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderDomainService {

    private final IOrderRepository orderRepository;
    private final OrderCreateCheckChain orderCreateCheckChain;
    private final OrderPricingService orderPricingService;
    private final OrderFactory orderFactory;

    public OrderDomainService(IOrderRepository orderRepository,
                              OrderCreateCheckChain orderCreateCheckChain,
                              OrderPricingService orderPricingService,
                              OrderFactory orderFactory) {
        this.orderRepository = orderRepository;
        this.orderCreateCheckChain = orderCreateCheckChain;
        this.orderPricingService = orderPricingService;
        this.orderFactory = orderFactory;
    }

    public CreateOrderResult createNormalOrder(CreateOrderCommand command) {
        try {
            OrderCreateContext context = buildCreateContext(TradeTypeConstants.NORMAL, command);
            orderCreateCheckChain.checkNormalOrder(context);

            PackageTradeSnapshot snapshot = context.getPackageSnapshot();
            OrderPricingResult pricingResult = orderPricingService.calculate(context);

            DiningOrderEntity order = orderFactory.createOrder(context.getTradeType(), command, snapshot, pricingResult);
            DiningOrderEntity savedOrder = orderRepository.saveOrder(order);

            DiningOrderItemEntity orderItem = orderFactory.createOrderItem(savedOrder, snapshot, command.getQuantity());
            orderRepository.saveOrderItem(orderItem);

            return buildCreateOrderResult(savedOrder);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("normal order create failed", e);
        }
    }

    public OrderDetailEntity queryOrderDetail(Long orderId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId required");
        }
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        OrderDetailEntity detail = new OrderDetailEntity();
        detail.setOrder(order);
        detail.setItems(orderRepository.listOrderItems(orderId));
        return detail;
    }

    private OrderCreateContext buildCreateContext(String tradeType, CreateOrderCommand command) {
        OrderCreateContext context = new OrderCreateContext();
        context.setTradeType(tradeType);
        context.setCommand(command);
        return context;
    }

    private CreateOrderResult buildCreateOrderResult(DiningOrderEntity savedOrder) {
        CreateOrderResult result = new CreateOrderResult();
        result.setOrderId(savedOrder.getId());
        result.setOrderNo(savedOrder.getOrderNo());
        result.setPayAmount(savedOrder.getPayAmount());
        result.setOrderStatus(savedOrder.getOrderStatus());
        return result;
    }
}
