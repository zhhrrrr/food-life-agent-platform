package com.foodlife.trade.domain.order.groupbuy.refund;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyOrderListEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyStatusConstants;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.refund.strategy.IGroupBuyRefundStrategy;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GroupBuyRefundStrategyRouter {

    private static final String PAID_UNFORMED_STRATEGY = "paidUnformedGroupBuyRefundStrategy";
    private static final String PAID_FORMED_STRATEGY = "paidFormedGroupBuyRefundStrategy";

    private final IGroupBuyRepository groupBuyRepository;
    private final Map<String, IGroupBuyRefundStrategy> refundStrategyMap;

    public GroupBuyRefundStrategyRouter(IGroupBuyRepository groupBuyRepository,
                                        Map<String, IGroupBuyRefundStrategy> refundStrategyMap) {
        this.groupBuyRepository = groupBuyRepository;
        this.refundStrategyMap = refundStrategyMap;
    }

    public OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command, DiningOrderEntity order) {
        GroupBuyOrderListEntity orderList = groupBuyRepository.queryOrderListByOrderIdAndUserId(order.getId(), order.getUserId());
        if (orderList == null) {
            throw new IllegalArgumentException("group buy order list not found");
        }
        if (!GroupBuyStatusConstants.PAID.equals(orderList.getOrderStatus())) {
            throw new IllegalArgumentException("group buy order status can not refund");
        }

        GroupBuyTeamEntity team = groupBuyRepository.queryTeamByTeamId(orderList.getTeamId());
        if (team == null) {
            throw new IllegalArgumentException("group buy team not found");
        }

        String strategyName = routeStrategy(team.getTeamStatus());
        IGroupBuyRefundStrategy strategy = refundStrategyMap.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("group buy refund strategy not found");
        }
        return strategy.refundOrder(command, order);
    }

    private String routeStrategy(String teamStatus) {
        if (GroupBuyStatusConstants.IN_PROGRESS.equals(teamStatus)) {
            return PAID_UNFORMED_STRATEGY;
        }
        if (GroupBuyStatusConstants.SUCCESS.equals(teamStatus) || GroupBuyStatusConstants.COMPLETE_FAIL.equals(teamStatus)) {
            return PAID_FORMED_STRATEGY;
        }
        throw new IllegalArgumentException("group buy team status can not refund");
    }
}
