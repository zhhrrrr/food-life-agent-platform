package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.CancelOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderTimeoutCloseDetail;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderTimeoutCloseResult;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentOrderTimeoutCloseService {

    private static final int DEFAULT_TIMEOUT_MINUTES = 30;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final IPaymentOrderRepository paymentOrderRepository;
    private final IOrderRepository orderRepository;
    private final OrderDomainService orderDomainService;

    public PaymentOrderTimeoutCloseService(IPaymentOrderRepository paymentOrderRepository,
                                           IOrderRepository orderRepository,
                                           OrderDomainService orderDomainService) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.orderRepository = orderRepository;
        this.orderDomainService = orderDomainService;
    }

    public PaymentOrderTimeoutCloseResult closeTimeoutPreparedPaymentOrders(Integer timeoutMinutes, Integer limit) {
        LocalDateTime now = LocalDateTime.now();
        int normalizedTimeoutMinutes = normalizeTimeoutMinutes(timeoutMinutes);
        int normalizedLimit = normalizeLimit(limit);
        LocalDateTime timeoutBefore = now.minusMinutes(normalizedTimeoutMinutes);

        List<PaymentOrderEntity> paymentOrders = paymentOrderRepository.listTimeoutPreparedPaymentOrders(timeoutBefore, normalizedLimit);
        PaymentOrderTimeoutCloseResult result = new PaymentOrderTimeoutCloseResult();
        result.setCompensateTime(now);
        result.setTimeoutMinutes(normalizedTimeoutMinutes);
        result.setTimeoutBefore(timeoutBefore);
        result.setScannedPaymentCount(paymentOrders.size());
        result.setClosedPaymentCount(0);
        result.setCanceledOrderCount(0);
        result.setFailedPaymentCount(0);

        for (PaymentOrderEntity paymentOrder : paymentOrders) {
            PaymentOrderTimeoutCloseDetail detail = closeOnePaymentOrder(paymentOrder);
            result.getDetails().add(detail);
            if (Boolean.TRUE.equals(detail.getPaymentClosed())) {
                result.setClosedPaymentCount(result.getClosedPaymentCount() + 1);
            }
            if (Boolean.TRUE.equals(detail.getOrderCanceled())) {
                result.setCanceledOrderCount(result.getCanceledOrderCount() + 1);
            }
            if (detail.getFailReason() != null) {
                result.setFailedPaymentCount(result.getFailedPaymentCount() + 1);
            }
        }
        return result;
    }

    private PaymentOrderTimeoutCloseDetail closeOnePaymentOrder(PaymentOrderEntity paymentOrder) {
        PaymentOrderTimeoutCloseDetail detail = buildDetail(paymentOrder);
        try {
            DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(paymentOrder.getOrderId(), paymentOrder.getUserId());
            if (order != null) {
                detail.setBeforeOrderStatus(order.getOrderStatus());
                detail.setAfterOrderStatus(order.getOrderStatus());
            }

            boolean closed = paymentOrderRepository.markPayClosed(paymentOrder.getPayOrderNo(), PaymentOrderStatusConstants.PREPARED);
            detail.setPaymentClosed(closed);
            detail.setAfterPayStatus(closed ? PaymentOrderStatusConstants.CLOSED : paymentOrder.getPayStatus());
            if (!closed) {
                detail.setFailReason("payment status can not close");
                return detail;
            }

            if (order == null) {
                detail.setFailReason("order not found");
                return detail;
            }
            if (!OrderStatusConstants.WAIT_PAY.equals(order.getOrderStatus())) {
                return detail;
            }

            CancelOrderResult cancelOrderResult = orderDomainService.cancelOrder(order.getId(), order.getUserId());
            detail.setOrderCanceled(true);
            detail.setAfterOrderStatus(cancelOrderResult.getOrderStatus());
            return detail;
        } catch (Exception e) {
            detail.setFailReason(e.getMessage());
            return detail;
        }
    }

    private PaymentOrderTimeoutCloseDetail buildDetail(PaymentOrderEntity paymentOrder) {
        PaymentOrderTimeoutCloseDetail detail = new PaymentOrderTimeoutCloseDetail();
        detail.setPayOrderNo(paymentOrder.getPayOrderNo());
        detail.setOrderId(paymentOrder.getOrderId());
        detail.setOrderNo(paymentOrder.getOrderNo());
        detail.setUserId(paymentOrder.getUserId());
        detail.setPayAmount(paymentOrder.getPayAmount());
        detail.setBeforePayStatus(paymentOrder.getPayStatus());
        detail.setAfterPayStatus(paymentOrder.getPayStatus());
        detail.setPaymentClosed(false);
        detail.setOrderCanceled(false);
        return detail;
    }

    private int normalizeTimeoutMinutes(Integer timeoutMinutes) {
        if (timeoutMinutes == null || timeoutMinutes <= 0) {
            return DEFAULT_TIMEOUT_MINUTES;
        }
        return timeoutMinutes;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
