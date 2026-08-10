package com.foodlife.trade.domain.order.groupbuy.filter;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyActivityEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockContext;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class GroupBuyActivityUsabilityRuleFilter implements GroupBuyLockRuleFilter {

    private final IGroupBuyRepository groupBuyRepository;

    public GroupBuyActivityUsabilityRuleFilter(IGroupBuyRepository groupBuyRepository) {
        this.groupBuyRepository = groupBuyRepository;
    }

    @Override
    public Void apply(GroupBuyLockContext requestParameter, GroupBuyLockContext dynamicContext) {
        Long packageId = requestParameter.getCommand().getPackageId();
        GroupBuyActivityEntity activity = groupBuyRepository.queryActiveActivityByPackageId(packageId);
        if (activity == null) {
            throw new IllegalArgumentException("group buy activity not found");
        }
        if (activity.getActivityStatus() == null || activity.getActivityStatus() != 1) {
            throw new IllegalArgumentException("group buy activity disabled");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getValidStartTime()) || now.isAfter(activity.getValidEndTime())) {
            throw new IllegalArgumentException("group buy activity not in valid time");
        }
        if (activity.getStock() == null || activity.getStock() <= 0) {
            throw new IllegalArgumentException("group buy activity stock not enough");
        }
        dynamicContext.setActivity(activity);
        return next(requestParameter, dynamicContext);
    }
}
