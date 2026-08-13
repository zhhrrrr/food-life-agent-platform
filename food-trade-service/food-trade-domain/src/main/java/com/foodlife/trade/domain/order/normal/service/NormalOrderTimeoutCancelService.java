package com.foodlife.trade.domain.order.normal.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.normal.model.NormalOrderTimeoutCancelDetail;
import com.foodlife.trade.domain.order.normal.model.NormalOrderTimeoutCancelResult;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NormalOrderTimeoutCancelService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;
    private static final int MIN_TIMEOUT_MINUTES = 1;
    private static final int MAX_TIMEOUT_MINUTES = 1440;

    private final IOrderRepository orderRepository;
    private final NormalPackageStockMessageService normalPackageStockMessageService;

    public NormalOrderTimeoutCancelService(IOrderRepository orderRepository,
                                           NormalPackageStockMessageService normalPackageStockMessageService) {
        this.orderRepository = orderRepository;
        this.normalPackageStockMessageService = normalPackageStockMessageService;
    }

    public NormalOrderTimeoutCancelResult cancelTimeoutOrders(Integer timeoutMinutes, Integer limit) {
        LocalDateTime now = LocalDateTime.now();
        int normalizedTimeoutMinutes = normalizeTimeoutMinutes(timeoutMinutes);
        int normalizedLimit = normalizeLimit(limit);
        LocalDateTime timeoutBefore = now.minusMinutes(normalizedTimeoutMinutes);

        List<DiningOrderEntity> timeoutOrders = orderRepository.listTimeoutNormalWaitPayOrders(timeoutBefore, normalizedLimit);

        NormalOrderTimeoutCancelResult result = new NormalOrderTimeoutCancelResult();
        result.setCompensateTime(now);
        result.setTimeoutMinutes(normalizedTimeoutMinutes);
        result.setTimeoutBefore(timeoutBefore);
        result.setScannedOrderCount(timeoutOrders.size());
        result.setCanceledOrderCount(0);
        result.setReleaseStockMessageCount(0);
        result.setFailedOrderCount(0);

        for (DiningOrderEntity order : timeoutOrders) {
            NormalOrderTimeoutCancelDetail detail = cancelSingleOrder(order);
            result.getDetails().add(detail);
            if (Boolean.TRUE.equals(detail.getCanceled())) {
                result.setCanceledOrderCount(result.getCanceledOrderCount() + 1);
            }
            if (Boolean.TRUE.equals(detail.getReleaseStockMessageSent())) {
                result.setReleaseStockMessageCount(result.getReleaseStockMessageCount() + 1);
            }
            if (detail.getFailReason() != null) {
                result.setFailedOrderCount(result.getFailedOrderCount() + 1);
            }
        }

        return result;
    }

    private NormalOrderTimeoutCancelDetail cancelSingleOrder(DiningOrderEntity order) {
        NormalOrderTimeoutCancelDetail detail = buildDetail(order);
        try {
            boolean success = orderRepository.updateOrderStatus(order.getId(), OrderStatusConstants.WAIT_PAY, OrderStatusConstants.CANCELED);
            detail.setCanceled(success);
            if (!success) {
                detail.setFailReason("order status changed");
                return detail;
            }

            detail.setAfterOrderStatus(OrderStatusConstants.CANCELED);
            normalPackageStockMessageService.releaseStock(order);
            detail.setReleaseStockMessageSent(true);
            return detail;
        } catch (Exception e) {
            detail.setFailReason(e.getMessage());
            return detail;
        }
    }

    private NormalOrderTimeoutCancelDetail buildDetail(DiningOrderEntity order) {
        NormalOrderTimeoutCancelDetail detail = new NormalOrderTimeoutCancelDetail();
        detail.setOrderId(order.getId());
        detail.setOrderNo(order.getOrderNo());
        detail.setUserId(order.getUserId());
        detail.setPackageId(order.getPackageId());
        detail.setQuantity(order.getQuantity());
        detail.setBeforeOrderStatus(order.getOrderStatus());
        detail.setAfterOrderStatus(order.getOrderStatus());
        detail.setCanceled(false);
        detail.setReleaseStockMessageSent(false);
        return detail;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeTimeoutMinutes(Integer timeoutMinutes) {
        if (timeoutMinutes == null) {
            return DEFAULT_TIMEOUT_MINUTES;
        }
        if (timeoutMinutes < MIN_TIMEOUT_MINUTES) {
            return MIN_TIMEOUT_MINUTES;
        }
        return Math.min(timeoutMinutes, MAX_TIMEOUT_MINUTES);
    }
}
