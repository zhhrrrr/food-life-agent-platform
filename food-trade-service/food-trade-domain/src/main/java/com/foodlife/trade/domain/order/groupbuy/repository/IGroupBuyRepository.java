package com.foodlife.trade.domain.order.groupbuy.repository;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyActivityEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockAggregate;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockResult;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;

import java.time.LocalDateTime;

public interface IGroupBuyRepository {

    GroupBuyActivityEntity queryActiveActivityByPackageId(Long packageId);

    GroupBuyTeamEntity queryTeamByTeamId(String teamId);

    int queryUserTakeOrderCount(Long activityId, Long userId);

    boolean occupyActivityStock(Long activityId);

    GroupBuyLockResult saveGroupBuyLockOrder(GroupBuyLockAggregate aggregate);

    GroupBuyTeamEntity settlementGroupBuyPaySuccess(DiningOrderEntity order, LocalDateTime outTradeTime);

    void cancelUnpaidGroupBuyOrder(DiningOrderEntity order);
}
