package com.foodlife.trade.domain.order.groupbuy.refund.strategy;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import org.springframework.stereotype.Service;

@Service("paidFormedGroupBuyRefundStrategy")
public class PaidFormedGroupBuyRefundStrategy extends AbstractGroupBuyRefundStrategy {

    public PaidFormedGroupBuyRefundStrategy(IGroupBuyRepository groupBuyRepository) {
        super(groupBuyRepository);
    }

    @Override
    public OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command, DiningOrderEntity order) {
        GroupBuyTeamEntity team = groupBuyRepository.refundPaidFormedGroupBuyOrder(order);
        return buildSuccessBehavior(command, order, team);
    }
}
