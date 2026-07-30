package com.foodlife.trade.domain.order.service;

import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.create.OrderCreateTemplateRouter;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderDetailEntity;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderDomainService {

    private final IOrderRepository orderRepository;
    private final OrderCreateTemplateRouter orderCreateTemplateRouter;

    public OrderDomainService(IOrderRepository orderRepository,
                              OrderCreateTemplateRouter orderCreateTemplateRouter) {
        this.orderRepository = orderRepository;
        this.orderCreateTemplateRouter = orderCreateTemplateRouter;
    }

    public CreateOrderResult createNormalOrder(CreateOrderCommand command) {
        return orderCreateTemplateRouter.create(TradeTypeConstants.NORMAL, command);
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

}
