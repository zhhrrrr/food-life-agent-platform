package com.foodlife.trade.domain.order.groupbuy.refund.strategy;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import org.springframework.stereotype.Service;

@Service("paidUnformedGroupBuyRefundStrategy")
public class PaidUnformedGroupBuyRefundStrategy extends AbstractGroupBuyRefundStrategy {

    public PaidUnformedGroupBuyRefundStrategy(IGroupBuyRepository groupBuyRepository) {
        super(groupBuyRepository);
    }

    @Override
    public OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command, DiningOrderEntity order) {
        GroupBuyTeamEntity team = groupBuyRepository.refundPaidUnformedGroupBuyOrder(order);
        return buildSuccessBehavior(command, order, team);
    }
}
