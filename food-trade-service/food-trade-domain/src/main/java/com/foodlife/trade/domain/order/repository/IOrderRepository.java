package com.foodlife.trade.domain.order.repository;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;

public interface IOrderRepository {

    DiningOrderEntity saveOrder(DiningOrderEntity order);

    void saveOrderItem(DiningOrderItemEntity orderItem);

    DiningOrderEntity findOrderByIdAndUserId(Long orderId, Long userId);

    java.util.List<DiningOrderEntity> listUserOrders(Long userId, Long lastId, Integer pageSize);

    boolean updateOrderStatus(Long orderId, String fromStatus, String toStatus);

    java.util.List<DiningOrderItemEntity> listOrderItems(Long orderId);
}
