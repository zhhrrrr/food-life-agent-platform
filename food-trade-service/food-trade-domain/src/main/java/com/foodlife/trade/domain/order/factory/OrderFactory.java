package com.foodlife.trade.domain.order.factory;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.sequence.OrderNoGenerator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderFactory {

    private final OrderNoGenerator orderNoGenerator;

    public OrderFactory(OrderNoGenerator orderNoGenerator) {
        this.orderNoGenerator = orderNoGenerator;
    }

    public DiningOrderEntity createOrder(String tradeType, CreateOrderCommand command, PackageTradeSnapshot snapshot, OrderPricingResult pricingResult) {
        LocalDateTime now = LocalDateTime.now();
        DiningOrderEntity order = new DiningOrderEntity();
        order.setOrderNo(orderNoGenerator.generate(command.getUserId()));
        order.setUserId(command.getUserId());
        order.setShopId(snapshot.getShopId());
        order.setPackageId(snapshot.getPackageId());
        order.setQuantity(command.getQuantity());
        order.setTotalAmount(pricingResult.getTotalAmount());
        order.setPayAmount(pricingResult.getPayAmount());
        order.setTradeType(tradeType);
        order.setOrderStatus(OrderStatusConstants.WAIT_PAY);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        return order;
    }

    public DiningOrderItemEntity createOrderItem(DiningOrderEntity order, PackageTradeSnapshot snapshot, Integer quantity) {
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
}
