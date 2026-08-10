package com.foodlife.trade.domain.order.groupbuy.factory;

import com.foodlife.patterns.framework.link.model2.LinkArmory;
import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.groupbuy.filter.GroupBuyActivityUsabilityRuleFilter;
import com.foodlife.trade.domain.order.groupbuy.filter.GroupBuyCommandRuleFilter;
import com.foodlife.trade.domain.order.groupbuy.filter.GroupBuyPackageSnapshotRuleFilter;
import com.foodlife.trade.domain.order.groupbuy.filter.GroupBuyTeamUsabilityRuleFilter;
import com.foodlife.trade.domain.order.groupbuy.filter.GroupBuyUserTakeLimitRuleFilter;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyLockRuleFilterFactory {

    @Bean("groupBuyLockRuleFilter")
    public BusinessLinkedList<GroupBuyLockContext, GroupBuyLockContext, Void> groupBuyLockRuleFilter(
            GroupBuyCommandRuleFilter groupBuyCommandRuleFilter,
            GroupBuyActivityUsabilityRuleFilter groupBuyActivityUsabilityRuleFilter,
            GroupBuyUserTakeLimitRuleFilter groupBuyUserTakeLimitRuleFilter,
            GroupBuyTeamUsabilityRuleFilter groupBuyTeamUsabilityRuleFilter,
            GroupBuyPackageSnapshotRuleFilter groupBuyPackageSnapshotRuleFilter) {

        return new LinkArmory<>(
                "group buy lock order rule filter chain",
                groupBuyCommandRuleFilter,
                groupBuyActivityUsabilityRuleFilter,
                groupBuyUserTakeLimitRuleFilter,
                groupBuyTeamUsabilityRuleFilter,
                groupBuyPackageSnapshotRuleFilter
        ).getLogicLink();
    }
}
