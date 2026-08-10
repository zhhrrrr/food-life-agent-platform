package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyActivityEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockAggregate;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockResult;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyOrderListEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyStatusConstants;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.infrastructure.dao.IDiningOrderItemMapper;
import com.foodlife.trade.infrastructure.dao.IDiningOrderMapper;
import com.foodlife.trade.infrastructure.dao.IGroupBuyActivityMapper;
import com.foodlife.trade.infrastructure.dao.IGroupBuyOrderListMapper;
import com.foodlife.trade.infrastructure.dao.IGroupBuyTeamMapper;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderItemPO;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderPO;
import com.foodlife.trade.infrastructure.dao.po.GroupBuyActivityPO;
import com.foodlife.trade.infrastructure.dao.po.GroupBuyOrderListPO;
import com.foodlife.trade.infrastructure.dao.po.GroupBuyTeamPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public class GroupBuyRepository implements IGroupBuyRepository {

    private final IGroupBuyActivityMapper groupBuyActivityMapper;
    private final IGroupBuyTeamMapper groupBuyTeamMapper;
    private final IGroupBuyOrderListMapper groupBuyOrderListMapper;
    private final IDiningOrderMapper diningOrderMapper;
    private final IDiningOrderItemMapper diningOrderItemMapper;

    public GroupBuyRepository(IGroupBuyActivityMapper groupBuyActivityMapper,
                              IGroupBuyTeamMapper groupBuyTeamMapper,
                              IGroupBuyOrderListMapper groupBuyOrderListMapper,
                              IDiningOrderMapper diningOrderMapper,
                              IDiningOrderItemMapper diningOrderItemMapper) {
        this.groupBuyActivityMapper = groupBuyActivityMapper;
        this.groupBuyTeamMapper = groupBuyTeamMapper;
        this.groupBuyOrderListMapper = groupBuyOrderListMapper;
        this.diningOrderMapper = diningOrderMapper;
        this.diningOrderItemMapper = diningOrderItemMapper;
    }

    @Override
    public GroupBuyActivityEntity queryActiveActivityByPackageId(Long packageId) {
        GroupBuyActivityPO po = groupBuyActivityMapper.selectOne(new LambdaQueryWrapper<GroupBuyActivityPO>()
                .eq(GroupBuyActivityPO::getPackageId, packageId)
                .last("limit 1"));
        return toActivityEntity(po);
    }

    @Override
    public GroupBuyTeamEntity queryTeamByTeamId(String teamId) {
        GroupBuyTeamPO po = groupBuyTeamMapper.selectOne(new LambdaQueryWrapper<GroupBuyTeamPO>()
                .eq(GroupBuyTeamPO::getTeamId, teamId)
                .last("limit 1"));
        return toTeamEntity(po);
    }

    @Override
    public int queryUserTakeOrderCount(Long activityId, Long userId) {
        Long count = groupBuyOrderListMapper.selectCount(new LambdaQueryWrapper<GroupBuyOrderListPO>()
                .eq(GroupBuyOrderListPO::getActivityId, activityId)
                .eq(GroupBuyOrderListPO::getUserId, userId)
                .in(GroupBuyOrderListPO::getOrderStatus, GroupBuyStatusConstants.LOCKED, GroupBuyStatusConstants.PAID));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public boolean occupyActivityStock(Long activityId) {
        int updated = groupBuyActivityMapper.update(null, new LambdaUpdateWrapper<GroupBuyActivityPO>()
                .setSql("stock = stock - 1")
                .set(GroupBuyActivityPO::getUpdateTime, LocalDateTime.now())
                .eq(GroupBuyActivityPO::getId, activityId)
                .gt(GroupBuyActivityPO::getStock, 0));
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyLockResult saveGroupBuyLockOrder(GroupBuyLockAggregate aggregate) {
        if (!occupyActivityStock(aggregate.getTeam().getActivityId())) {
            throw new IllegalArgumentException("group buy activity stock not enough");
        }

        GroupBuyTeamEntity team = aggregate.getTeam();
        if (aggregate.isNewTeam()) {
            team.setLockCount(1);
            GroupBuyTeamPO teamPO = toTeamPO(team);
            groupBuyTeamMapper.insert(teamPO);
            team.setId(teamPO.getId());
        } else {
            int updated = groupBuyTeamMapper.update(null, new LambdaUpdateWrapper<GroupBuyTeamPO>()
                    .setSql("lock_count = lock_count + 1")
                    .set(GroupBuyTeamPO::getUpdateTime, LocalDateTime.now())
                    .eq(GroupBuyTeamPO::getTeamId, team.getTeamId())
                    .eq(GroupBuyTeamPO::getTeamStatus, GroupBuyStatusConstants.IN_PROGRESS)
                    .lt(GroupBuyTeamPO::getLockCount, team.getTargetCount()));
            if (updated <= 0) {
                throw new IllegalArgumentException("group buy team full");
            }
            team = queryTeamByTeamId(team.getTeamId());
        }

        DiningOrderPO orderPO = toOrderPO(aggregate.getOrder());
        diningOrderMapper.insert(orderPO);

        DiningOrderItemEntity orderItem = aggregate.getOrderItem();
        orderItem.setOrderId(orderPO.getId());
        diningOrderItemMapper.insert(toOrderItemPO(orderItem));

        GroupBuyOrderListEntity orderList = aggregate.getOrderList();
        orderList.setOrderId(orderPO.getId());
        orderList.setOrderNo(orderPO.getOrderNo());
        groupBuyOrderListMapper.insert(toOrderListPO(orderList));

        return toLockResult(orderPO, team);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyTeamEntity settlementGroupBuyPaySuccess(DiningOrderEntity order, LocalDateTime outTradeTime) {
        int orderUpdated = diningOrderMapper.update(null, new LambdaUpdateWrapper<DiningOrderPO>()
                .set(DiningOrderPO::getOrderStatus, OrderStatusConstants.PAID)
                .set(DiningOrderPO::getUpdateTime, LocalDateTime.now())
                .eq(DiningOrderPO::getId, order.getId())
                .eq(DiningOrderPO::getUserId, order.getUserId())
                .eq(DiningOrderPO::getOrderStatus, OrderStatusConstants.WAIT_PAY));
        if (orderUpdated <= 0) {
            throw new IllegalArgumentException("order status can not pay");
        }

        GroupBuyOrderListPO orderListPO = groupBuyOrderListMapper.selectOne(new LambdaQueryWrapper<GroupBuyOrderListPO>()
                .eq(GroupBuyOrderListPO::getOrderId, order.getId())
                .eq(GroupBuyOrderListPO::getUserId, order.getUserId())
                .last("limit 1"));
        if (orderListPO == null) {
            throw new IllegalArgumentException("group buy order list not found");
        }

        int orderListUpdated = groupBuyOrderListMapper.update(null, new LambdaUpdateWrapper<GroupBuyOrderListPO>()
                .set(GroupBuyOrderListPO::getOrderStatus, GroupBuyStatusConstants.PAID)
                .set(GroupBuyOrderListPO::getOutTradeTime, outTradeTime)
                .set(GroupBuyOrderListPO::getUpdateTime, LocalDateTime.now())
                .eq(GroupBuyOrderListPO::getId, orderListPO.getId())
                .eq(GroupBuyOrderListPO::getOrderStatus, GroupBuyStatusConstants.LOCKED));
        if (orderListUpdated <= 0) {
            throw new IllegalArgumentException("group buy order status can not pay");
        }

        int teamUpdated = groupBuyTeamMapper.update(null, new LambdaUpdateWrapper<GroupBuyTeamPO>()
                .setSql("complete_count = complete_count + 1")
                .set(GroupBuyTeamPO::getUpdateTime, LocalDateTime.now())
                .eq(GroupBuyTeamPO::getTeamId, orderListPO.getTeamId())
                .eq(GroupBuyTeamPO::getTeamStatus, GroupBuyStatusConstants.IN_PROGRESS)
                .apply("complete_count < target_count"));
        if (teamUpdated <= 0) {
            throw new IllegalArgumentException("group buy team can not settlement");
        }

        GroupBuyTeamEntity team = queryTeamByTeamId(orderListPO.getTeamId());
        if (team.getCompleteCount() >= team.getTargetCount()) {
            groupBuyTeamMapper.update(null, new LambdaUpdateWrapper<GroupBuyTeamPO>()
                    .set(GroupBuyTeamPO::getTeamStatus, GroupBuyStatusConstants.SUCCESS)
                    .set(GroupBuyTeamPO::getUpdateTime, LocalDateTime.now())
                    .eq(GroupBuyTeamPO::getTeamId, orderListPO.getTeamId())
                    .eq(GroupBuyTeamPO::getTeamStatus, GroupBuyStatusConstants.IN_PROGRESS)
                    .apply("complete_count >= target_count"));
            team = queryTeamByTeamId(orderListPO.getTeamId());
        }
        return team;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelUnpaidGroupBuyOrder(DiningOrderEntity order) {
        GroupBuyOrderListPO orderListPO = groupBuyOrderListMapper.selectOne(new LambdaQueryWrapper<GroupBuyOrderListPO>()
                .eq(GroupBuyOrderListPO::getOrderId, order.getId())
                .eq(GroupBuyOrderListPO::getUserId, order.getUserId())
                .last("limit 1"));
        if (orderListPO == null) {
            throw new IllegalArgumentException("group buy order list not found");
        }

        int orderUpdated = diningOrderMapper.update(null, new LambdaUpdateWrapper<DiningOrderPO>()
                .set(DiningOrderPO::getOrderStatus, OrderStatusConstants.CANCELED)
                .set(DiningOrderPO::getUpdateTime, LocalDateTime.now())
                .eq(DiningOrderPO::getId, order.getId())
                .eq(DiningOrderPO::getUserId, order.getUserId())
                .eq(DiningOrderPO::getOrderStatus, OrderStatusConstants.WAIT_PAY));
        if (orderUpdated <= 0) {
            throw new IllegalArgumentException("order status can not cancel");
        }

        int orderListUpdated = groupBuyOrderListMapper.update(null, new LambdaUpdateWrapper<GroupBuyOrderListPO>()
                .set(GroupBuyOrderListPO::getOrderStatus, GroupBuyStatusConstants.CANCELED)
                .set(GroupBuyOrderListPO::getUpdateTime, LocalDateTime.now())
                .eq(GroupBuyOrderListPO::getId, orderListPO.getId())
                .eq(GroupBuyOrderListPO::getOrderStatus, GroupBuyStatusConstants.LOCKED));
        if (orderListUpdated <= 0) {
            throw new IllegalArgumentException("group buy order status can not cancel");
        }

        int teamUpdated = groupBuyTeamMapper.update(null, new LambdaUpdateWrapper<GroupBuyTeamPO>()
                .setSql("lock_count = lock_count - 1")
                .set(GroupBuyTeamPO::getUpdateTime, LocalDateTime.now())
                .eq(GroupBuyTeamPO::getTeamId, orderListPO.getTeamId())
                .eq(GroupBuyTeamPO::getTeamStatus, GroupBuyStatusConstants.IN_PROGRESS)
                .gt(GroupBuyTeamPO::getLockCount, 0));
        if (teamUpdated <= 0) {
            throw new IllegalArgumentException("group buy team lock count can not rollback");
        }

        groupBuyActivityMapper.update(null, new LambdaUpdateWrapper<GroupBuyActivityPO>()
                .setSql("stock = stock + 1")
                .set(GroupBuyActivityPO::getUpdateTime, LocalDateTime.now())
                .eq(GroupBuyActivityPO::getId, orderListPO.getActivityId()));
    }

    private GroupBuyLockResult toLockResult(DiningOrderPO orderPO, GroupBuyTeamEntity team) {
        GroupBuyLockResult result = new GroupBuyLockResult();
        result.setOrderId(orderPO.getId());
        result.setOrderNo(orderPO.getOrderNo());
        result.setTeamId(team.getTeamId());
        result.setActivityId(team.getActivityId());
        result.setPayAmount(orderPO.getPayAmount());
        result.setOrderStatus(orderPO.getOrderStatus());
        result.setTeamStatus(team.getTeamStatus());
        result.setTargetCount(team.getTargetCount());
        result.setLockCount(team.getLockCount());
        result.setCompleteCount(team.getCompleteCount());
        return result;
    }

    private GroupBuyActivityEntity toActivityEntity(GroupBuyActivityPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyActivityEntity entity = new GroupBuyActivityEntity();
        entity.setId(po.getId());
        entity.setPackageId(po.getPackageId());
        entity.setActivityName(po.getActivityName());
        entity.setTargetCount(po.getTargetCount());
        entity.setUserTakeLimit(po.getUserTakeLimit());
        entity.setGroupPrice(po.getGroupPrice());
        entity.setActivityStatus(po.getActivityStatus());
        entity.setValidStartTime(po.getValidStartTime());
        entity.setValidEndTime(po.getValidEndTime());
        entity.setStock(po.getStock());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private GroupBuyTeamEntity toTeamEntity(GroupBuyTeamPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyTeamEntity entity = new GroupBuyTeamEntity();
        entity.setId(po.getId());
        entity.setTeamId(po.getTeamId());
        entity.setActivityId(po.getActivityId());
        entity.setPackageId(po.getPackageId());
        entity.setTargetCount(po.getTargetCount());
        entity.setCompleteCount(po.getCompleteCount());
        entity.setLockCount(po.getLockCount());
        entity.setTeamStatus(po.getTeamStatus());
        entity.setValidStartTime(po.getValidStartTime());
        entity.setValidEndTime(po.getValidEndTime());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private GroupBuyTeamPO toTeamPO(GroupBuyTeamEntity entity) {
        GroupBuyTeamPO po = new GroupBuyTeamPO();
        po.setId(entity.getId());
        po.setTeamId(entity.getTeamId());
        po.setActivityId(entity.getActivityId());
        po.setPackageId(entity.getPackageId());
        po.setTargetCount(entity.getTargetCount());
        po.setCompleteCount(entity.getCompleteCount());
        po.setLockCount(entity.getLockCount());
        po.setTeamStatus(entity.getTeamStatus());
        po.setValidStartTime(entity.getValidStartTime());
        po.setValidEndTime(entity.getValidEndTime());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private GroupBuyOrderListPO toOrderListPO(GroupBuyOrderListEntity entity) {
        GroupBuyOrderListPO po = new GroupBuyOrderListPO();
        po.setId(entity.getId());
        po.setUserId(entity.getUserId());
        po.setTeamId(entity.getTeamId());
        po.setOrderId(entity.getOrderId());
        po.setOrderNo(entity.getOrderNo());
        po.setActivityId(entity.getActivityId());
        po.setPackageId(entity.getPackageId());
        po.setOrderStatus(entity.getOrderStatus());
        po.setOutTradeTime(entity.getOutTradeTime());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private DiningOrderPO toOrderPO(DiningOrderEntity entity) {
        DiningOrderPO po = new DiningOrderPO();
        po.setId(entity.getId());
        po.setOrderNo(entity.getOrderNo());
        po.setUserId(entity.getUserId());
        po.setShopId(entity.getShopId());
        po.setPackageId(entity.getPackageId());
        po.setQuantity(entity.getQuantity());
        po.setTotalAmount(entity.getTotalAmount());
        po.setPayAmount(entity.getPayAmount());
        po.setTradeType(entity.getTradeType());
        po.setOrderStatus(entity.getOrderStatus());
        po.setUseTime(entity.getUseTime());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private DiningOrderItemPO toOrderItemPO(DiningOrderItemEntity entity) {
        DiningOrderItemPO po = new DiningOrderItemPO();
        po.setId(entity.getId());
        po.setOrderId(entity.getOrderId());
        po.setShopId(entity.getShopId());
        po.setShopNameSnapshot(entity.getShopNameSnapshot());
        po.setPackageId(entity.getPackageId());
        po.setPackageNameSnapshot(entity.getPackageNameSnapshot());
        po.setPackageDescriptionSnapshot(entity.getPackageDescriptionSnapshot());
        po.setCoverImageSnapshot(entity.getCoverImageSnapshot());
        po.setPackagePriceSnapshot(entity.getPackagePriceSnapshot());
        po.setActualPrice(entity.getActualPrice());
        po.setQuantity(entity.getQuantity());
        po.setUseRuleSnapshot(entity.getUseRuleSnapshot());
        return po;
    }
}
