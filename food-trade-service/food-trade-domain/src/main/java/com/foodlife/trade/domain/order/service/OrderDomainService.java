package com.foodlife.trade.domain.order.service;

import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderDetailEntity;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OrderDomainService {

    private final IBusinessPackagePort businessPackagePort;
    private final IOrderRepository orderRepository;

    public OrderDomainService(IBusinessPackagePort businessPackagePort, IOrderRepository orderRepository) {
        this.businessPackagePort = businessPackagePort;
        this.orderRepository = orderRepository;
    }

    public CreateOrderResult createNormalOrder(CreateOrderCommand command) {
        validateCommand(command);

        PackageTradeSnapshot snapshot = businessPackagePort.queryTradeSnapshot(command.getPackageId());
        if (snapshot == null) {
            throw new IllegalArgumentException("package not found");
        }
        if (snapshot.getPackageStatus() == null || snapshot.getPackageStatus() != 1) {
            throw new IllegalArgumentException("package offline");
        }
        if (snapshot.getStock() == null || snapshot.getStock() < command.getQuantity()) {
            throw new IllegalArgumentException("package stock not enough");
        }

        Long totalAmount = snapshot.getPrice() * command.getQuantity();
        DiningOrderEntity order = buildOrder(command, snapshot, totalAmount);
        DiningOrderEntity savedOrder = orderRepository.saveOrder(order);

        DiningOrderItemEntity orderItem = buildOrderItem(savedOrder, snapshot, command.getQuantity());
        orderRepository.saveOrderItem(orderItem);

        CreateOrderResult result = new CreateOrderResult();
        result.setOrderId(savedOrder.getId());
        result.setOrderNo(savedOrder.getOrderNo());
        result.setPayAmount(savedOrder.getPayAmount());
        result.setOrderStatus(savedOrder.getOrderStatus());
        return result;
    }

    public OrderDetailEntity queryOrderDetail(Long orderId, Long userId) {
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
        OrderDetailEntity detail = new OrderDetailEntity();
        detail.setOrder(order);
        detail.setItems(orderRepository.listOrderItems(orderId));
        return detail;
    }

    private void validateCommand(CreateOrderCommand command) {
        if (command == null || command.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (command.getPackageId() == null) {
            throw new IllegalArgumentException("packageId required");
        }
        if (command.getQuantity() == null || command.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity invalid");
        }
    }

    private DiningOrderEntity buildOrder(CreateOrderCommand command, PackageTradeSnapshot snapshot, Long totalAmount) {
        DiningOrderEntity order = new DiningOrderEntity();
        order.setOrderNo(generateOrderNo(command.getUserId()));
        order.setUserId(command.getUserId());
        order.setShopId(snapshot.getShopId());
        order.setPackageId(snapshot.getPackageId());
        order.setQuantity(command.getQuantity());
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setTradeType("NORMAL");
        order.setOrderStatus("WAIT_PAY");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }

    private DiningOrderItemEntity buildOrderItem(DiningOrderEntity order, PackageTradeSnapshot snapshot, Integer quantity) {
        DiningOrderItemEntity item = new DiningOrderItemEntity();
        item.setOrderId(order.getId());
        item.setShopId(snapshot.getShopId());
        item.setShopNameSnapshot(snapshot.getShopName());
        item.setPackageId(snapshot.getPackageId());
        item.setPackageNameSnapshot(snapshot.getPackageName());
        item.setPackageDescriptionSnapshot(snapshot.getPackageDescription());
        item.setCoverImageSnapshot(snapshot.getCoverImage());
        item.setPackagePriceSnapshot(snapshot.getPrice());
        item.setActualPrice(order.getPayAmount());
        item.setQuantity(quantity);
        item.setUseRuleSnapshot(snapshot.getUseRule());
        return item;
    }

    private String generateOrderNo(Long userId) {
        return "NO" + System.currentTimeMillis() + userId;
    }
}
