package com.foodlife.trade.domain.order.service.use;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderUseCommandEntity;
import com.foodlife.trade.domain.order.model.OrderUseResult;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OrderUseService {

    private final IOrderRepository orderRepository;

    public OrderUseService(IOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderUseResult useOrder(OrderUseCommandEntity command) {
        if (command == null || command.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (command.getOrderId() == null) {
            throw new IllegalArgumentException("orderId required");
        }
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(command.getOrderId(), command.getUserId());
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (OrderStatusConstants.USED.equals(order.getOrderStatus())) {
            return buildResult(command, order, "repeat", order.getUseTime());
        }
        if (!OrderStatusConstants.PAID.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order status can not use");
        }

        LocalDateTime useTime = LocalDateTime.now();
        boolean success = orderRepository.updateOrderStatusAndUseTime(order.getId(), OrderStatusConstants.PAID, OrderStatusConstants.USED, useTime);
        if (!success) {
            throw new IllegalArgumentException("order status can not use");
        }
        return buildResult(command, order, "success", useTime);
    }

    private OrderUseResult buildResult(OrderUseCommandEntity command, DiningOrderEntity order, String behavior, LocalDateTime useTime) {
        OrderUseResult result = new OrderUseResult();
        result.setUserId(command.getUserId());
        result.setOrderId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setOrderStatus(OrderStatusConstants.USED);
        result.setUseBehavior(behavior);
        result.setUseTime(useTime);
        return result;
    }
}
