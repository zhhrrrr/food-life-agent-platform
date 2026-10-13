package com.foodlife.trade.domain.order.payment.repository;

import com.foodlife.trade.domain.order.payment.model.RefundOrderEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface IRefundOrderRepository {

    RefundOrderEntity save(RefundOrderEntity refundOrder);

    RefundOrderEntity findByPayOrderNo(String payOrderNo);

    RefundOrderEntity findByRefundOrderNo(String refundOrderNo);

    boolean markRefundProcessing(String refundOrderNo, String fromStatus);

    boolean markRefundSuccess(String refundOrderNo, String fromStatus, String outRefundNo, LocalDateTime refundTime);

    boolean markRefundFailed(String refundOrderNo, String fromStatus, String failReason);

    List<RefundOrderEntity> listPreparedRefundOrders(Integer limit);

    List<RefundOrderEntity> listRecentRefundOrders(Integer limit);

    boolean recoverStaleProcessing(String refundOrderNo, LocalDateTime timeoutBefore);

    List<RefundOrderEntity> listRecoverableRefundOrders(LocalDateTime now, Integer limit);

    boolean claimRecoveryAttempt(String refundOrderNo, LocalDateTime now, LocalDateTime nextRetryTime);

    void markBusinessCompleted(String refundOrderNo);

    boolean resetRecovery(String refundOrderNo);

    void recordRecoveryFailure(String refundOrderNo, String reason);
}
