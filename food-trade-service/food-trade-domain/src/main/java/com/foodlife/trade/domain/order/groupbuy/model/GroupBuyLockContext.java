package com.foodlife.trade.domain.order.groupbuy.model;

import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import lombok.Data;

import java.io.Serializable;

@Data
public class GroupBuyLockContext implements Serializable {

    private GroupBuyLockOrderCommand command;
    private GroupBuyActivityEntity activity;
    private PackageTradeSnapshot packageSnapshot;
    private GroupBuyTeamEntity team;
    private Integer userTakeOrderCount;
}
