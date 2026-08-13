package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.infrastructure.dao.IPaymentOrderMapper;
import com.foodlife.trade.infrastructure.dao.po.PaymentOrderPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class PaymentOrderRepository implements IPaymentOrderRepository {

    private final IPaymentOrderMapper paymentOrderMapper;

    public PaymentOrderRepository(IPaymentOrderMapper paymentOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
    }

    @Override
    public PaymentOrderEntity save(PaymentOrderEntity paymentOrder) {
        PaymentOrderPO po = toPO(paymentOrder);
        paymentOrderMapper.insert(po);
        paymentOrder.setId(po.getId());
        return paymentOrder;
    }

    @Override
    public PaymentOrderEntity findByOrderIdAndUserId(Long orderId, Long userId) {
        PaymentOrderPO po = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrderPO>()
                .eq(PaymentOrderPO::getOrderId, orderId)
                .eq(PaymentOrderPO::getUserId, userId)
                .last("limit 1"));
        return toEntity(po);
    }

    @Override
    public PaymentOrderEntity findByPayOrderNo(String payOrderNo) {
        PaymentOrderPO po = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrderPO>()
                .eq(PaymentOrderPO::getPayOrderNo, payOrderNo)
                .last("limit 1"));
        return toEntity(po);
    }

    @Override
    public boolean markPaySuccess(String payOrderNo, String fromStatus, String outTradeNo, LocalDateTime payTime) {
        PaymentOrderPO updatePO = new PaymentOrderPO();
        updatePO.setPayStatus(com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants.SUCCESS);
        updatePO.setOutTradeNo(outTradeNo);
        updatePO.setPayTime(payTime);
        updatePO.setUpdateTime(LocalDateTime.now());
        int updated = paymentOrderMapper.update(updatePO, new LambdaUpdateWrapper<PaymentOrderPO>()
                .eq(PaymentOrderPO::getPayOrderNo, payOrderNo)
                .eq(PaymentOrderPO::getPayStatus, fromStatus));
        return updated > 0;
    }

    private PaymentOrderPO toPO(PaymentOrderEntity entity) {
        PaymentOrderPO po = new PaymentOrderPO();
        po.setId(entity.getId());
        po.setPayOrderNo(entity.getPayOrderNo());
        po.setOrderId(entity.getOrderId());
        po.setOrderNo(entity.getOrderNo());
        po.setUserId(entity.getUserId());
        po.setSource(entity.getSource());
        po.setChannel(entity.getChannel());
        po.setPayAmount(entity.getPayAmount());
        po.setPayStatus(entity.getPayStatus());
        po.setOutTradeNo(entity.getOutTradeNo());
        po.setPayTime(entity.getPayTime());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private PaymentOrderEntity toEntity(PaymentOrderPO po) {
        if (po == null) {
            return null;
        }
        PaymentOrderEntity entity = new PaymentOrderEntity();
        entity.setId(po.getId());
        entity.setPayOrderNo(po.getPayOrderNo());
        entity.setOrderId(po.getOrderId());
        entity.setOrderNo(po.getOrderNo());
        entity.setUserId(po.getUserId());
        entity.setSource(po.getSource());
        entity.setChannel(po.getChannel());
        entity.setPayAmount(po.getPayAmount());
        entity.setPayStatus(po.getPayStatus());
        entity.setOutTradeNo(po.getOutTradeNo());
        entity.setPayTime(po.getPayTime());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }
}
