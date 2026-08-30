package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderUseRecordEntity;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.infrastructure.dao.IDiningOrderItemMapper;
import com.foodlife.trade.infrastructure.dao.IDiningOrderMapper;
import com.foodlife.trade.infrastructure.dao.IGroupBuyOrderListMapper;
import com.foodlife.trade.infrastructure.dao.IGroupBuyTeamMapper;
import com.foodlife.trade.infrastructure.dao.IOrderUseRecordMapper;
import com.foodlife.trade.infrastructure.dao.ISeckillOrderMapper;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderItemPO;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderPO;
import com.foodlife.trade.infrastructure.dao.po.GroupBuyOrderListPO;
import com.foodlife.trade.infrastructure.dao.po.GroupBuyTeamPO;
import com.foodlife.trade.infrastructure.dao.po.OrderUseRecordPO;
import com.foodlife.trade.infrastructure.dao.po.SeckillOrderPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class OrderRepository implements IOrderRepository {

    private final IDiningOrderMapper diningOrderMapper;
    private final IDiningOrderItemMapper diningOrderItemMapper;
    private final IGroupBuyOrderListMapper groupBuyOrderListMapper;
    private final IGroupBuyTeamMapper groupBuyTeamMapper;
    private final ISeckillOrderMapper seckillOrderMapper;
    private final IOrderUseRecordMapper orderUseRecordMapper;

    public OrderRepository(IDiningOrderMapper diningOrderMapper,
                           IDiningOrderItemMapper diningOrderItemMapper,
                           IGroupBuyOrderListMapper groupBuyOrderListMapper,
                           IGroupBuyTeamMapper groupBuyTeamMapper,
                           ISeckillOrderMapper seckillOrderMapper,
                           IOrderUseRecordMapper orderUseRecordMapper) {
        this.diningOrderMapper = diningOrderMapper;
        this.diningOrderItemMapper = diningOrderItemMapper;
        this.groupBuyOrderListMapper = groupBuyOrderListMapper;
        this.groupBuyTeamMapper = groupBuyTeamMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.orderUseRecordMapper = orderUseRecordMapper;
    }

    @Override
    public DiningOrderEntity saveOrder(DiningOrderEntity order) {
        DiningOrderPO orderPO = toOrderPO(order);
        diningOrderMapper.insert(orderPO);
        order.setId(orderPO.getId());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrderItem(DiningOrderItemEntity orderItem) {
        diningOrderItemMapper.insert(toOrderItemPO(orderItem));
    }

    @Override
    public DiningOrderEntity findOrderByIdAndUserId(Long orderId, Long userId) {
        DiningOrderPO orderPO = diningOrderMapper.selectOne(new LambdaQueryWrapper<DiningOrderPO>()
                .eq(DiningOrderPO::getId, orderId)
                .eq(DiningOrderPO::getUserId, userId)
                .last("limit 1"));
        return toOrderEntity(orderPO);
    }

    @Override
    public List<DiningOrderEntity> listUserOrders(Long userId, Long lastId, Integer pageSize) {
        return listUserOrders(userId, lastId, pageSize, null, null);
    }

    @Override
    public List<DiningOrderEntity> listUserOrders(Long userId, Long lastId, Integer pageSize,
                                                  String tradeType, String orderStatus) {
        LambdaQueryWrapper<DiningOrderPO> queryWrapper = new LambdaQueryWrapper<DiningOrderPO>()
                .eq(DiningOrderPO::getUserId, userId)
                .orderByAsc(DiningOrderPO::getId)
                .last("limit " + pageSize);
        if (lastId != null) {
            queryWrapper.gt(DiningOrderPO::getId, lastId);
        }
        if (tradeType != null) {
            queryWrapper.eq(DiningOrderPO::getTradeType, tradeType);
        }
        if (orderStatus != null) {
            queryWrapper.eq(DiningOrderPO::getOrderStatus, orderStatus);
        }
        return diningOrderMapper.selectList(queryWrapper)
                .stream()
                .map(this::toOrderEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<DiningOrderEntity> listTimeoutNormalWaitPayOrders(LocalDateTime timeoutBefore, Integer limit) {
        return diningOrderMapper.selectList(new LambdaQueryWrapper<DiningOrderPO>()
                        .eq(DiningOrderPO::getTradeType, TradeTypeConstants.NORMAL)
                        .eq(DiningOrderPO::getOrderStatus, OrderStatusConstants.WAIT_PAY)
                        .le(DiningOrderPO::getCreateTime, timeoutBefore)
                        .orderByAsc(DiningOrderPO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toOrderEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateOrderStatus(Long orderId, String fromStatus, String toStatus) {
        DiningOrderPO updatePO = new DiningOrderPO();
        updatePO.setOrderStatus(toStatus);
        updatePO.setUpdateTime(LocalDateTime.now());
        int updated = diningOrderMapper.update(updatePO, new LambdaUpdateWrapper<DiningOrderPO>()
                .eq(DiningOrderPO::getId, orderId)
                .eq(DiningOrderPO::getOrderStatus, fromStatus));
        return updated > 0;
    }

    @Override
    public boolean updateOrderStatusAndUseTime(Long orderId, String fromStatus, String toStatus, LocalDateTime useTime) {
        DiningOrderPO updatePO = new DiningOrderPO();
        updatePO.setOrderStatus(toStatus);
        updatePO.setUseTime(useTime);
        updatePO.setUpdateTime(LocalDateTime.now());
        int updated = diningOrderMapper.update(updatePO, new LambdaUpdateWrapper<DiningOrderPO>()
                .eq(DiningOrderPO::getId, orderId)
                .eq(DiningOrderPO::getOrderStatus, fromStatus));
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderUseRecordEntity usePaidOrder(DiningOrderEntity order, LocalDateTime useTime, String useRecordNo) {
        boolean success = updateOrderStatusAndUseTime(order.getId(), OrderStatusConstants.PAID, OrderStatusConstants.USED, useTime);
        if (!success) {
            throw new IllegalArgumentException("order status can not use");
        }
        syncSubOrderUseStatus(order);

        OrderUseRecordPO record = new OrderUseRecordPO();
        record.setUseRecordNo(useRecordNo);
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setUserId(order.getUserId());
        record.setShopId(order.getShopId());
        record.setPackageId(order.getPackageId());
        record.setTradeType(order.getTradeType());
        record.setUseSource("MOCK_MERCHANT");
        record.setUseStatus("SUCCESS");
        record.setUseTime(useTime);
        orderUseRecordMapper.insert(record);
        return toUseRecordEntity(orderUseRecordMapper.selectById(record.getId()));
    }

    @Override
    public OrderUseRecordEntity findUseRecordByOrderId(Long orderId) {
        OrderUseRecordPO record = orderUseRecordMapper.selectOne(new LambdaQueryWrapper<OrderUseRecordPO>()
                .eq(OrderUseRecordPO::getOrderId, orderId)
                .last("limit 1"));
        return toUseRecordEntity(record);
    }

    @Override
    public List<DiningOrderItemEntity> listOrderItems(Long orderId) {
        return diningOrderItemMapper.selectList(new LambdaQueryWrapper<DiningOrderItemPO>()
                        .eq(DiningOrderItemPO::getOrderId, orderId))
                .stream()
                .map(this::toOrderItemEntity)
                .collect(Collectors.toList());
    }

    private void syncSubOrderUseStatus(DiningOrderEntity order) {
        if (TradeTypeConstants.GROUP_BUY.equals(order.getTradeType())) {
            GroupBuyOrderListPO orderList = groupBuyOrderListMapper.selectOne(new LambdaQueryWrapper<GroupBuyOrderListPO>()
                    .eq(GroupBuyOrderListPO::getOrderId, order.getId())
                    .last("limit 1"));
            if (orderList == null) {
                throw new IllegalArgumentException("group buy order not found");
            }
            GroupBuyTeamPO team = groupBuyTeamMapper.selectOne(new LambdaQueryWrapper<GroupBuyTeamPO>()
                    .eq(GroupBuyTeamPO::getTeamId, orderList.getTeamId())
                    .last("limit 1"));
            if (team == null || !GroupBuyStatusConstants.SUCCESS.equals(team.getTeamStatus())) {
                throw new IllegalArgumentException("group buy team not success");
            }
            int updated = groupBuyOrderListMapper.update(null, new LambdaUpdateWrapper<GroupBuyOrderListPO>()
                    .set(GroupBuyOrderListPO::getOrderStatus, GroupBuyStatusConstants.USED)
                    .eq(GroupBuyOrderListPO::getOrderId, order.getId())
                    .eq(GroupBuyOrderListPO::getOrderStatus, GroupBuyStatusConstants.PAID));
            if (updated <= 0) {
                throw new IllegalArgumentException("group buy order status can not use");
            }
        }
        if (TradeTypeConstants.SECKILL.equals(order.getTradeType())) {
            int updated = seckillOrderMapper.update(null, new LambdaUpdateWrapper<SeckillOrderPO>()
                    .set(SeckillOrderPO::getOrderStatus, OrderStatusConstants.USED)
                    .eq(SeckillOrderPO::getOrderId, order.getId())
                    .eq(SeckillOrderPO::getOrderStatus, OrderStatusConstants.PAID));
            if (updated <= 0) {
                throw new IllegalArgumentException("seckill order status can not use");
            }
        }
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
        po.setDiscountAmount(entity.getDiscountAmount());
        po.setPayAmount(entity.getPayAmount());
        po.setUserCouponId(entity.getUserCouponId());
        po.setTradeType(entity.getTradeType());
        po.setOrderStatus(entity.getOrderStatus());
        po.setUseTime(entity.getUseTime());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private DiningOrderEntity toOrderEntity(DiningOrderPO po) {
        if (po == null) {
            return null;
        }
        DiningOrderEntity entity = new DiningOrderEntity();
        entity.setId(po.getId());
        entity.setOrderNo(po.getOrderNo());
        entity.setUserId(po.getUserId());
        entity.setShopId(po.getShopId());
        entity.setPackageId(po.getPackageId());
        entity.setQuantity(po.getQuantity());
        entity.setTotalAmount(po.getTotalAmount());
        entity.setDiscountAmount(po.getDiscountAmount());
        entity.setPayAmount(po.getPayAmount());
        entity.setUserCouponId(po.getUserCouponId());
        entity.setTradeType(po.getTradeType());
        entity.setOrderStatus(po.getOrderStatus());
        entity.setUseTime(po.getUseTime());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
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

    private DiningOrderItemEntity toOrderItemEntity(DiningOrderItemPO po) {
        DiningOrderItemEntity entity = new DiningOrderItemEntity();
        entity.setId(po.getId());
        entity.setOrderId(po.getOrderId());
        entity.setShopId(po.getShopId());
        entity.setShopNameSnapshot(po.getShopNameSnapshot());
        entity.setPackageId(po.getPackageId());
        entity.setPackageNameSnapshot(po.getPackageNameSnapshot());
        entity.setPackageDescriptionSnapshot(po.getPackageDescriptionSnapshot());
        entity.setCoverImageSnapshot(po.getCoverImageSnapshot());
        entity.setPackagePriceSnapshot(po.getPackagePriceSnapshot());
        entity.setActualPrice(po.getActualPrice());
        entity.setQuantity(po.getQuantity());
        entity.setUseRuleSnapshot(po.getUseRuleSnapshot());
        return entity;
    }

    private OrderUseRecordEntity toUseRecordEntity(OrderUseRecordPO po) {
        if (po == null) {
            return null;
        }
        OrderUseRecordEntity entity = new OrderUseRecordEntity();
        entity.setId(po.getId());
        entity.setUseRecordNo(po.getUseRecordNo());
        entity.setOrderId(po.getOrderId());
        entity.setOrderNo(po.getOrderNo());
        entity.setUserId(po.getUserId());
        entity.setShopId(po.getShopId());
        entity.setPackageId(po.getPackageId());
        entity.setTradeType(po.getTradeType());
        entity.setUseSource(po.getUseSource());
        entity.setUseStatus(po.getUseStatus());
        entity.setUseTime(po.getUseTime());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }
}
