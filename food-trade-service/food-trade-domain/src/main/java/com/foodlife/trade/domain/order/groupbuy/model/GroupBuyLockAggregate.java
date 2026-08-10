package com.foodlife.trade.domain.order.groupbuy.model;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import lombok.Data;

import java.io.Serializable;

@Data
public class GroupBuyLockAggregate implements Serializable {

    private DiningOrderEntity order;
    private DiningOrderItemEntity orderItem;
    private GroupBuyTeamEntity team;
    private GroupBuyOrderListEntity orderList;
    private boolean newTeam;
}
