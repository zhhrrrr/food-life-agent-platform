package com.foodlife.trade.domain.order.payment.repository;

import com.foodlife.trade.domain.order.payment.model.RefundOrderEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface IRefundOrderRepository {

    RefundOrderEntity save(RefundOrderEntity refundOrder);

    RefundOrderEntity findByPayOrderNo(String payOrderNo);

    RefundOrderEntity findByRefundOrderNo(String refundOrderNo);

    boolean markRefundSuccess(String refundOrderNo, String fromStatus, String outRefundNo, LocalDateTime refundTime);

    boolean markRefundFailed(String refundOrderNo, String fromStatus, String failReason);

    List<RefundOrderEntity> listPreparedRefundOrders(Integer limit);

    List<RefundOrderEntity> listRecentRefundOrders(Integer limit);
}
