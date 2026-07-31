package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.CancelOrderResponseDTO;
import com.foodlife.trade.api.dto.CreateOrderRequestDTO;
import com.foodlife.trade.api.dto.CreateOrderResponseDTO;
import com.foodlife.trade.api.dto.OrderDetailResponseDTO;
import com.foodlife.trade.api.dto.OrderItemResponseDTO;
import com.foodlife.trade.api.dto.OrderListResponseDTO;
import com.foodlife.trade.api.dto.PayOrderRequestDTO;
import com.foodlife.trade.api.dto.PayOrderResponseDTO;
import com.foodlife.trade.domain.order.model.CancelOrderResult;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderDetailEntity;
import com.foodlife.trade.domain.order.model.OrderListResult;
import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import com.foodlife.trade.domain.order.model.OrderPaySuccessEntity;
import com.foodlife.trade.domain.order.model.OrderSummaryEntity;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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

    @GetMapping("/orders")
    public Response<OrderListResponseDTO> queryUserOrderList(@RequestParam(required = false) Long lastId,
                                                             @RequestParam(required = false) Integer pageSize) {
        try {
            OrderListResult result = orderDomainService.queryUserOrderList(UserHolder.getUserId(), lastId, pageSize);
            return Response.success(toOrderListResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Response<CancelOrderResponseDTO> cancelOrder(@PathVariable Long orderId) {
        try {
            CancelOrderResult result = orderDomainService.cancelOrder(orderId, UserHolder.getUserId());
            return Response.success(toCancelResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/{orderId}/pay/mock")
    public Response<PayOrderResponseDTO> payOrderMock(@PathVariable Long orderId,
                                                      @RequestBody(required = false) PayOrderRequestDTO request) {
        try {
            OrderPaySettlementEntity result = orderDomainService.payOrderMock(toPaySuccessEntity(orderId, request));
            return Response.success(toPayResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
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

    private CancelOrderResponseDTO toCancelResponse(CancelOrderResult result) {
        CancelOrderResponseDTO response = new CancelOrderResponseDTO();
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setOrderStatus(result.getOrderStatus());
        return response;
    }

    private OrderPaySuccessEntity toPaySuccessEntity(Long orderId, PayOrderRequestDTO request) {
        OrderPaySuccessEntity entity = new OrderPaySuccessEntity();
        entity.setSource(readOrDefault(request == null ? null : request.getSource(), "FOOD_LIFE"));
        entity.setChannel(readOrDefault(request == null ? null : request.getChannel(), "MOCK_PAY"));
        entity.setUserId(UserHolder.getUserId());
        entity.setOrderId(orderId);
        entity.setOutTradeNo(readOrDefault(request == null ? null : request.getOutTradeNo(), "MOCK" + System.currentTimeMillis()));
        entity.setOutTradeTime(LocalDateTime.now());
        return entity;
    }

    private PayOrderResponseDTO toPayResponse(OrderPaySettlementEntity result) {
        PayOrderResponseDTO response = new PayOrderResponseDTO();
        response.setSource(result.getSource());
        response.setChannel(result.getChannel());
        response.setUserId(result.getUserId());
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setOrderStatus(result.getOrderStatus());
        response.setOutTradeNo(result.getOutTradeNo());
        response.setOutTradeTime(result.getOutTradeTime());
        return response;
    }

    private String readOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private OrderListResponseDTO toOrderListResponse(OrderListResult result) {
        OrderListResponseDTO response = new OrderListResponseDTO();
        response.setOrders(result.getOrders().stream().map(this::toOrderInfoResponse).collect(Collectors.toList()));
        response.setHasMore(result.getHasMore());
        response.setLastId(result.getLastId());
        return response;
    }

    private OrderListResponseDTO.OrderInfo toOrderInfoResponse(OrderSummaryEntity order) {
        OrderListResponseDTO.OrderInfo response = new OrderListResponseDTO.OrderInfo();
        response.setOrderId(order.getOrderId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setShopId(order.getShopId());
        response.setShopNameSnapshot(order.getShopNameSnapshot());
        response.setPackageId(order.getPackageId());
        response.setPackageNameSnapshot(order.getPackageNameSnapshot());
        response.setCoverImageSnapshot(order.getCoverImageSnapshot());
        response.setQuantity(order.getQuantity());
        response.setTotalAmount(order.getTotalAmount());
        response.setPayAmount(order.getPayAmount());
        response.setTradeType(order.getTradeType());
        response.setOrderStatus(order.getOrderStatus());
        response.setCreateTime(order.getCreateTime());
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
