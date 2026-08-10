package com.foodlife.trade.domain.order.groupbuy.service;

import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.groupbuy.factory.GroupBuyLockRuleFilterFactory;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyActivityEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockAggregate;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockContext;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockOrderCommand;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockResult;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyOrderListEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyStatusConstants;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class GroupBuyLockOrderService {

    private final IGroupBuyRepository groupBuyRepository;
    private final OrderFactory orderFactory;
    private final BusinessLinkedList<GroupBuyLockContext, GroupBuyLockContext, Void> groupBuyLockRuleFilter;

    public GroupBuyLockOrderService(IGroupBuyRepository groupBuyRepository,
                                    OrderFactory orderFactory,
                                    @Qualifier("groupBuyLockRuleFilter")
                                    BusinessLinkedList<GroupBuyLockContext, GroupBuyLockContext, Void> groupBuyLockRuleFilter) {
        this.groupBuyRepository = groupBuyRepository;
        this.orderFactory = orderFactory;
        this.groupBuyLockRuleFilter = groupBuyLockRuleFilter;
    }

    public GroupBuyLockResult lockOrder(GroupBuyLockOrderCommand command) {
        try {
            GroupBuyLockContext context = new GroupBuyLockContext();
            context.setCommand(command);
            groupBuyLockRuleFilter.apply(context, context);

            GroupBuyLockAggregate aggregate = buildAggregate(context);
            return groupBuyRepository.saveGroupBuyLockOrder(aggregate);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("group buy lock order failed", e);
        }
    }

    private GroupBuyLockAggregate buildAggregate(GroupBuyLockContext context) {
        GroupBuyActivityEntity activity = context.getActivity();
        PackageTradeSnapshot snapshot = context.getPackageSnapshot();
        GroupBuyLockOrderCommand command = context.getCommand();

        OrderPricingResult pricingResult = new OrderPricingResult();
        pricingResult.setTotalAmount(snapshot.getPrice() * command.getQuantity());
        pricingResult.setPayAmount(activity.getGroupPrice() * command.getQuantity());

        DiningOrderEntity order = orderFactory.createOrder(TradeTypeConstants.GROUP_BUY, toCreateOrderCommand(command), snapshot, pricingResult);
        DiningOrderItemEntity item = orderFactory.createOrderItem(order, snapshot, command.getQuantity());

        GroupBuyTeamEntity team = context.getTeam();
        boolean newTeam = team == null;
        if (newTeam) {
            team = createTeam(activity, snapshot);
        }

        GroupBuyOrderListEntity orderList = new GroupBuyOrderListEntity();
        orderList.setUserId(command.getUserId());
        orderList.setTeamId(team.getTeamId());
        orderList.setActivityId(activity.getId());
        orderList.setPackageId(command.getPackageId());
        orderList.setOrderStatus(GroupBuyStatusConstants.LOCKED);
        orderList.setCreateTime(LocalDateTime.now());
        orderList.setUpdateTime(orderList.getCreateTime());

        GroupBuyLockAggregate aggregate = new GroupBuyLockAggregate();
        aggregate.setOrder(order);
        aggregate.setOrderItem(item);
        aggregate.setTeam(team);
        aggregate.setOrderList(orderList);
        aggregate.setNewTeam(newTeam);
        return aggregate;
    }

    private com.foodlife.trade.domain.order.model.CreateOrderCommand toCreateOrderCommand(GroupBuyLockOrderCommand command) {
        com.foodlife.trade.domain.order.model.CreateOrderCommand createOrderCommand = new com.foodlife.trade.domain.order.model.CreateOrderCommand();
        createOrderCommand.setUserId(command.getUserId());
        createOrderCommand.setPackageId(command.getPackageId());
        createOrderCommand.setQuantity(command.getQuantity());
        return createOrderCommand;
    }

    private GroupBuyTeamEntity createTeam(GroupBuyActivityEntity activity, PackageTradeSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        GroupBuyTeamEntity team = new GroupBuyTeamEntity();
        team.setTeamId("GBT" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        team.setActivityId(activity.getId());
        team.setPackageId(snapshot.getPackageId());
        team.setTargetCount(activity.getTargetCount());
        team.setCompleteCount(0);
        team.setLockCount(0);
        team.setTeamStatus(GroupBuyStatusConstants.IN_PROGRESS);
        team.setValidStartTime(now);
        team.setValidEndTime(activity.getValidEndTime().isBefore(now.plusHours(24)) ? activity.getValidEndTime() : now.plusHours(24));
        team.setCreateTime(now);
        team.setUpdateTime(now);
        return team;
    }
}
