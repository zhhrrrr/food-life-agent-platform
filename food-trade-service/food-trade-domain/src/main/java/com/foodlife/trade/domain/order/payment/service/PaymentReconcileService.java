package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.constant.RefundOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentReconcileResult;
import com.foodlife.trade.domain.order.payment.model.RefundOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.payment.repository.IRefundOrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentReconcileService {

    private final IPaymentOrderRepository paymentOrderRepository;
    private final IRefundOrderRepository refundOrderRepository;

    public PaymentReconcileService(IPaymentOrderRepository paymentOrderRepository,
                                   IRefundOrderRepository refundOrderRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.refundOrderRepository = refundOrderRepository;
    }

    public PaymentReconcileResult reconcile(Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        List<PaymentOrderEntity> paymentOrders = paymentOrderRepository.listRecentPaymentOrders(safeLimit);
        List<RefundOrderEntity> refundOrders = refundOrderRepository.listRecentRefundOrders(safeLimit);

        PaymentReconcileResult result = new PaymentReconcileResult();
        result.setScannedPaymentCount(paymentOrders.size());
        result.setPreparedPaymentCount(countPaymentStatus(paymentOrders, PaymentOrderStatusConstants.PREPARED));
        result.setSuccessPaymentCount(countPaymentStatus(paymentOrders, PaymentOrderStatusConstants.SUCCESS));
        result.setClosedPaymentCount(countPaymentStatus(paymentOrders, PaymentOrderStatusConstants.CLOSED));
        result.setRefundedPaymentCount(countPaymentStatus(paymentOrders, PaymentOrderStatusConstants.REFUNDED));
        result.setScannedRefundCount(refundOrders.size());
        result.setPreparedRefundCount(countRefundStatus(refundOrders, RefundOrderStatusConstants.PREPARED));
        result.setSuccessRefundCount(countRefundStatus(refundOrders, RefundOrderStatusConstants.SUCCESS));
        result.setFailedRefundCount(countRefundStatus(refundOrders, RefundOrderStatusConstants.FAILED));
        result.setInconsistentPayOrderNos(findRefundPaymentInconsistency(paymentOrders, refundOrders));
        return result;
    }

    private Integer countPaymentStatus(List<PaymentOrderEntity> orders, String status) {
        return Math.toIntExact(orders.stream().filter(order -> status.equals(order.getPayStatus())).count());
    }

    private Integer countRefundStatus(List<RefundOrderEntity> orders, String status) {
        return Math.toIntExact(orders.stream().filter(order -> status.equals(order.getRefundStatus())).count());
    }

    private List<String> findRefundPaymentInconsistency(List<PaymentOrderEntity> paymentOrders,
                                                        List<RefundOrderEntity> refundOrders) {
        Map<String, PaymentOrderEntity> paymentOrderMap = paymentOrders.stream()
                .collect(Collectors.toMap(PaymentOrderEntity::getPayOrderNo, Function.identity(), (left, right) -> left));
        return refundOrders.stream()
                .filter(refundOrder -> RefundOrderStatusConstants.SUCCESS.equals(refundOrder.getRefundStatus()))
                .filter(refundOrder -> {
                    PaymentOrderEntity paymentOrder = paymentOrderMap.get(refundOrder.getPayOrderNo());
                    return paymentOrder == null || !PaymentOrderStatusConstants.REFUNDED.equals(paymentOrder.getPayStatus());
                })
                .map(RefundOrderEntity::getPayOrderNo)
                .collect(Collectors.toList());
    }
}

