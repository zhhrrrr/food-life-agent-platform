package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.CreateOrderRequestDTO;
import com.foodlife.trade.api.dto.CreateOrderResponseDTO;
import com.foodlife.trade.api.dto.OrderDetailResponseDTO;
import com.foodlife.trade.api.dto.OrderItemResponseDTO;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderDetailEntity;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade")
public class OrderController {

    private final OrderDomainService orderDomainService;

    public OrderController(OrderDomainService orderDomainService) {
        this.orderDomainService = orderDomainService;
    }

    @PostMapping("/orders/normal")
    public Response<CreateOrderResponseDTO> createNormalOrder(@RequestBody CreateOrderRequestDTO request) {
        try {
            CreateOrderResult result = orderDomainService.createNormalOrder(toCommand(request));
            return Response.success(toResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/orders/{orderId}")
    public Response<OrderDetailResponseDTO> queryOrderDetail(@PathVariable Long orderId) {
        try {
            OrderDetailEntity detail = orderDomainService.queryOrderDetail(orderId, UserHolder.getUserId());
            return Response.success(toDetailResponse(detail));
        } catch (IllegalArgumentException e) {
            return Response.fail("404", e.getMessage());
        }
    }

    private CreateOrderCommand toCommand(CreateOrderRequestDTO request) {
        CreateOrderCommand command = new CreateOrderCommand();
        command.setUserId(UserHolder.getUserId());
        command.setPackageId(request == null ? null : request.getPackageId());
        command.setQuantity(request == null ? null : request.getQuantity());
        return command;
    }

    private CreateOrderResponseDTO toResponse(CreateOrderResult result) {
        CreateOrderResponseDTO response = new CreateOrderResponseDTO();
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setPayAmount(result.getPayAmount());
        response.setOrderStatus(result.getOrderStatus());
        return response;
    }

    private OrderDetailResponseDTO toDetailResponse(OrderDetailEntity detail) {
        DiningOrderEntity order = detail.getOrder();
        OrderDetailResponseDTO response = new OrderDetailResponseDTO();
        response.setOrderId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setShopId(order.getShopId());
        response.setPackageId(order.getPackageId());
        response.setQuantity(order.getQuantity());
        response.setTotalAmount(order.getTotalAmount());
        response.setPayAmount(order.getPayAmount());
        response.setTradeType(order.getTradeType());
        response.setOrderStatus(order.getOrderStatus());
        response.setCreateTime(order.getCreateTime());
        response.setItems(toItemResponses(detail.getItems()));
        return response;
    }

    private List<OrderItemResponseDTO> toItemResponses(List<DiningOrderItemEntity> items) {
        return items.stream().map(this::toItemResponse).collect(Collectors.toList());
    }

    private OrderItemResponseDTO toItemResponse(DiningOrderItemEntity item) {
        OrderItemResponseDTO response = new OrderItemResponseDTO();
        response.setItemId(item.getId());
        response.setShopId(item.getShopId());
        response.setShopNameSnapshot(item.getShopNameSnapshot());
        response.setPackageId(item.getPackageId());
        response.setPackageNameSnapshot(item.getPackageNameSnapshot());
        response.setPackageDescriptionSnapshot(item.getPackageDescriptionSnapshot());
        response.setCoverImageSnapshot(item.getCoverImageSnapshot());
        response.setPackagePriceSnapshot(item.getPackagePriceSnapshot());
        response.setActualPrice(item.getActualPrice());
        response.setQuantity(item.getQuantity());
        response.setUseRuleSnapshot(item.getUseRuleSnapshot());
        return response;
    }
}
