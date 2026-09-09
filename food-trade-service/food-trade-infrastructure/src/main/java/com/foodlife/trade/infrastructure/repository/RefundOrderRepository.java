package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.payment.constant.RefundOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.RefundOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IRefundOrderRepository;
import com.foodlife.trade.infrastructure.dao.IRefundOrderMapper;
import com.foodlife.trade.infrastructure.dao.po.RefundOrderPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class RefundOrderRepository implements IRefundOrderRepository {

    private final IRefundOrderMapper refundOrderMapper;

    public RefundOrderRepository(IRefundOrderMapper refundOrderMapper) {
        this.refundOrderMapper = refundOrderMapper;
    }

    @Override
    public RefundOrderEntity save(RefundOrderEntity refundOrder) {
        RefundOrderPO po = toPO(refundOrder);
        refundOrderMapper.insert(po);
        refundOrder.setId(po.getId());
        return refundOrder;
    }

    @Override
    public RefundOrderEntity findByPayOrderNo(String payOrderNo) {
        RefundOrderPO po = refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getPayOrderNo, payOrderNo)
                .last("limit 1"));
        return toEntity(po);
    }

    @Override
    public RefundOrderEntity findByRefundOrderNo(String refundOrderNo) {
        RefundOrderPO po = refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundOrderNo, refundOrderNo)
                .last("limit 1"));
        return toEntity(po);
    }

    @Override
    public boolean markRefundSuccess(String refundOrderNo, String fromStatus, String outRefundNo, LocalDateTime refundTime) {
        RefundOrderPO updatePO = new RefundOrderPO();
        updatePO.setRefundStatus(RefundOrderStatusConstants.SUCCESS);
        updatePO.setOutRefundNo(outRefundNo);
        updatePO.setRefundTime(refundTime);
        updatePO.setUpdateTime(LocalDateTime.now());
        int updated = refundOrderMapper.update(updatePO, new LambdaUpdateWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundOrderNo, refundOrderNo)
                .eq(RefundOrderPO::getRefundStatus, fromStatus));
        return updated > 0;
    }

    @Override
    public boolean markRefundFailed(String refundOrderNo, String fromStatus, String failReason) {
        RefundOrderPO updatePO = new RefundOrderPO();
        updatePO.setRefundStatus(RefundOrderStatusConstants.FAILED);
        updatePO.setFailReason(failReason);
        updatePO.setUpdateTime(LocalDateTime.now());
        int updated = refundOrderMapper.update(updatePO, new LambdaUpdateWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundOrderNo, refundOrderNo)
                .eq(RefundOrderPO::getRefundStatus, fromStatus));
        return updated > 0;
    }

    @Override
    public List<RefundOrderEntity> listPreparedRefundOrders(Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 200 ? 50 : limit;
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                        .eq(RefundOrderPO::getRefundStatus, RefundOrderStatusConstants.PREPARED)
                        .orderByAsc(RefundOrderPO::getId)
                        .last("limit " + safeLimit))
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<RefundOrderEntity> listRecentRefundOrders(Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                        .orderByDesc(RefundOrderPO::getId)
                        .last("limit " + safeLimit))
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private RefundOrderPO toPO(RefundOrderEntity entity) {
        RefundOrderPO po = new RefundOrderPO();
        po.setId(entity.getId());
        po.setRefundOrderNo(entity.getRefundOrderNo());
        po.setPayOrderNo(entity.getPayOrderNo());
        po.setOrderId(entity.getOrderId());
        po.setOrderNo(entity.getOrderNo());
        po.setUserId(entity.getUserId());
        po.setSource(entity.getSource());
        po.setChannel(entity.getChannel());
        po.setRefundAmount(entity.getRefundAmount());
        po.setRefundStatus(entity.getRefundStatus());
        po.setRefundReason(entity.getRefundReason());
        po.setOutTradeNo(entity.getOutTradeNo());
        po.setOutRefundNo(entity.getOutRefundNo());
        po.setFailReason(entity.getFailReason());
        po.setRefundTime(entity.getRefundTime());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private RefundOrderEntity toEntity(RefundOrderPO po) {
        if (po == null) {
            return null;
        }
        RefundOrderEntity entity = new RefundOrderEntity();
        entity.setId(po.getId());
        entity.setRefundOrderNo(po.getRefundOrderNo());
        entity.setPayOrderNo(po.getPayOrderNo());
        entity.setOrderId(po.getOrderId());
        entity.setOrderNo(po.getOrderNo());
        entity.setUserId(po.getUserId());
        entity.setSource(po.getSource());
        entity.setChannel(po.getChannel());
        entity.setRefundAmount(po.getRefundAmount());
        entity.setRefundStatus(po.getRefundStatus());
        entity.setRefundReason(po.getRefundReason());
        entity.setOutTradeNo(po.getOutTradeNo());
        entity.setOutRefundNo(po.getOutRefundNo());
        entity.setFailReason(po.getFailReason());
        entity.setRefundTime(po.getRefundTime());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }
}
