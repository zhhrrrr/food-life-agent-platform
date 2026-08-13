package com.foodlife.trade.domain.order.normal.service;

import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.normal.constant.NormalPackageStockMessageConstants;
import com.foodlife.trade.domain.order.normal.model.NormalOrderTraceEntity;
import com.foodlife.trade.domain.order.normal.model.PackageStockChangeRecord;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NormalOrderTraceService {

    private final IOrderRepository orderRepository;
    private final IBusinessPackagePort businessPackagePort;
    private final NormalPackageStockMessageService normalPackageStockMessageService;

    public NormalOrderTraceService(IOrderRepository orderRepository,
                                   IBusinessPackagePort businessPackagePort,
                                   NormalPackageStockMessageService normalPackageStockMessageService) {
        this.orderRepository = orderRepository;
        this.businessPackagePort = businessPackagePort;
        this.normalPackageStockMessageService = normalPackageStockMessageService;
    }

    public NormalOrderTraceEntity queryTrace(Long orderId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId required");
        }
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (!TradeTypeConstants.NORMAL.equals(order.getTradeType())) {
            throw new IllegalArgumentException("order is not normal order");
        }

        List<TradeLocalMessageEntity> messages = normalPackageStockMessageService.queryMessages(orderId, null, 20);
        List<PackageStockChangeRecord> records = businessPackagePort.listStockChangeRecords(null, order.getPackageId(), 50);
        records.removeIf(record -> record.getOperationId() == null || !record.getOperationId().endsWith(":" + orderId));

        NormalOrderTraceEntity trace = new NormalOrderTraceEntity();
        trace.setOrder(order);
        trace.setOrderItems(orderRepository.listOrderItems(orderId));
        trace.setPackageSnapshot(businessPackagePort.queryTradeSnapshot(order.getPackageId()));
        trace.setStockMessages(messages);
        trace.setStockChangeRecords(records);
        trace.setCurrentStage(order.getOrderStatus());
        return trace;
    }
}
