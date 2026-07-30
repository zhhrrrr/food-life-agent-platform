package com.foodlife.trade.domain.order.repository;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;

public interface IOrderRepository {

    DiningOrderEntity saveOrder(DiningOrderEntity order);

    void saveOrderItem(DiningOrderItemEntity orderItem);
}
