package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OrderDetailEntity implements Serializable {

    private DiningOrderEntity order;
    private List<DiningOrderItemEntity> items;
}
