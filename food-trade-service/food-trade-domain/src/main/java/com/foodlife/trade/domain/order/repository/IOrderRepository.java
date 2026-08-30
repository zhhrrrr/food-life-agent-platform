package com.foodlife.trade.domain.order.repository;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderUseRecordEntity;

public interface IOrderRepository {

    DiningOrderEntity saveOrder(DiningOrderEntity order);

    void saveOrderItem(DiningOrderItemEntity orderItem);

    DiningOrderEntity findOrderByIdAndUserId(Long orderId, Long userId);

    java.util.List<DiningOrderEntity> listUserOrders(Long userId, Long lastId, Integer pageSize);

    java.util.List<DiningOrderEntity> listUserOrders(Long userId, Long lastId, Integer pageSize,
                                                     String tradeType, String orderStatus);

    java.util.List<DiningOrderEntity> listTimeoutNormalWaitPayOrders(java.time.LocalDateTime timeoutBefore, Integer limit);

    boolean updateOrderStatus(Long orderId, String fromStatus, String toStatus);

    boolean updateOrderStatusAndUseTime(Long orderId, String fromStatus, String toStatus, java.time.LocalDateTime useTime);

    OrderUseRecordEntity usePaidOrder(DiningOrderEntity order, java.time.LocalDateTime useTime, String useRecordNo);

    OrderUseRecordEntity findUseRecordByOrderId(Long orderId);

    java.util.List<DiningOrderItemEntity> listOrderItems(Long orderId);
}
