package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.CreateOrderRequestDTO;
import com.foodlife.trade.api.dto.CreateOrderResponseDTO;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
