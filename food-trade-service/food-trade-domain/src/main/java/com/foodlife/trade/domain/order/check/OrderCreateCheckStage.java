package com.foodlife.trade.domain.order.check;

import com.foodlife.trade.domain.order.constant.OrderPatternGroups;

public enum OrderCreateCheckStage {

    COMMAND(OrderPatternGroups.CREATE_COMMAND_CHECK),
    SNAPSHOT(OrderPatternGroups.CREATE_SNAPSHOT_CHECK);

    private final String group;

    OrderCreateCheckStage(String group) {
        this.group = group;
    }

    public String group() {
        return group;
    }
}
