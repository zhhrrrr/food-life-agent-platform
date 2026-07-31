package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.infrastructure.dao.IDiningOrderItemMapper;
import com.foodlife.trade.infrastructure.dao.IDiningOrderMapper;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderItemPO;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class OrderRepository implements IOrderRepository {

    private final IDiningOrderMapper diningOrderMapper;
    private final IDiningOrderItemMapper diningOrderItemMapper;

    public OrderRepository(IDiningOrderMapper diningOrderMapper, IDiningOrderItemMapper diningOrderItemMapper) {
        this.diningOrderMapper = diningOrderMapper;
        this.diningOrderItemMapper = diningOrderItemMapper;
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
        LambdaQueryWrapper<DiningOrderPO> queryWrapper = new LambdaQueryWrapper<DiningOrderPO>()
                .eq(DiningOrderPO::getUserId, userId)
                .orderByAsc(DiningOrderPO::getId)
                .last("limit " + pageSize);
        if (lastId != null) {
            queryWrapper.gt(DiningOrderPO::getId, lastId);
        }
        return diningOrderMapper.selectList(queryWrapper)
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
    public List<DiningOrderItemEntity> listOrderItems(Long orderId) {
        return diningOrderItemMapper.selectList(new LambdaQueryWrapper<DiningOrderItemPO>()
                        .eq(DiningOrderItemPO::getOrderId, orderId))
                .stream()
                .map(this::toOrderItemEntity)
                .collect(Collectors.toList());
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
        entity.setPayAmount(po.getPayAmount());
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
}
