package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderSettlementRuleFilterBackEntity implements Serializable {

    private DiningOrderEntity order;
}
