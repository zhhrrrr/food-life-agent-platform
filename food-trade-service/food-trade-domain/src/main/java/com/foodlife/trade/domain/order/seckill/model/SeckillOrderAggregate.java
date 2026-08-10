package com.foodlife.trade.domain.order.seckill.model;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderAggregate implements Serializable {

    private SeckillActivityEntity activity;
    private DiningOrderEntity order;
    private DiningOrderItemEntity orderItem;
    private SeckillOrderEntity seckillOrder;
}
