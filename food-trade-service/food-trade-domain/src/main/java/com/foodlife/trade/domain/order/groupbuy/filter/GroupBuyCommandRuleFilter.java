package com.foodlife.trade.domain.order.groupbuy.filter;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockContext;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockOrderCommand;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyCommandRuleFilter implements GroupBuyLockRuleFilter {

    @Override
    public Void apply(GroupBuyLockContext requestParameter, GroupBuyLockContext dynamicContext) {
        GroupBuyLockOrderCommand command = requestParameter.getCommand();
        if (command == null || command.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (command.getPackageId() == null) {
            throw new IllegalArgumentException("packageId required");
        }
        if (command.getQuantity() == null || command.getQuantity() != 1) {
            throw new IllegalArgumentException("group buy quantity must be 1");
        }
        return next(requestParameter, dynamicContext);
    }
}
