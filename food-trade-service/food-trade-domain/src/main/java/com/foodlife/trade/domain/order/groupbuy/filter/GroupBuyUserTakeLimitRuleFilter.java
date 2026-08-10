package com.foodlife.trade.domain.order.groupbuy.filter;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyActivityEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockContext;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyUserTakeLimitRuleFilter implements GroupBuyLockRuleFilter {

    private final IGroupBuyRepository groupBuyRepository;

    public GroupBuyUserTakeLimitRuleFilter(IGroupBuyRepository groupBuyRepository) {
        this.groupBuyRepository = groupBuyRepository;
    }

    @Override
    public Void apply(GroupBuyLockContext requestParameter, GroupBuyLockContext dynamicContext) {
        GroupBuyActivityEntity activity = dynamicContext.getActivity();
        int count = groupBuyRepository.queryUserTakeOrderCount(activity.getId(), requestParameter.getCommand().getUserId());
        if (activity.getUserTakeLimit() != null && count >= activity.getUserTakeLimit()) {
            throw new IllegalArgumentException("group buy take limit reached");
        }
        dynamicContext.setUserTakeOrderCount(count);
        return next(requestParameter, dynamicContext);
    }
}
