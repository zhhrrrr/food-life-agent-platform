package com.foodlife.trade.infrastructure.repository;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.infrastructure.dao.IDiningOrderItemMapper;
import com.foodlife.trade.infrastructure.dao.IDiningOrderMapper;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderItemPO;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
