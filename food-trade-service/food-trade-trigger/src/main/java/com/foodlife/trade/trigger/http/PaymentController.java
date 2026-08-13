package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.PayOrderResponseDTO;
import com.foodlife.trade.api.dto.PaymentCallbackRequestDTO;
import com.foodlife.trade.api.dto.PaymentCallbackResponseDTO;
import com.foodlife.trade.api.dto.PaymentOrderResponseDTO;
import com.foodlife.trade.api.dto.PaymentPrepareRequestDTO;
import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackResult;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentPrepareCommand;
import com.foodlife.trade.domain.order.payment.service.PaymentOrderService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade/pay")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    public PaymentController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @PostMapping("/orders/{orderId}/prepare")
    public Response<PaymentOrderResponseDTO> preparePayment(@PathVariable Long orderId,
                                                            @RequestBody(required = false) PaymentPrepareRequestDTO request) {
        try {
            PaymentOrderEntity result = paymentOrderService.preparePayment(toPrepareCommand(orderId, request));
            return Response.success(toPaymentOrderResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/callback/mock")
    public Response<PaymentCallbackResponseDTO> mockPaySuccessCallback(@RequestBody PaymentCallbackRequestDTO request) {
        try {
            PaymentCallbackResult result = paymentOrderService.handlePaySuccessCallback(toCallbackCommand(request));
            return Response.success(toCallbackResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/orders/{payOrderNo}")
    public Response<PaymentOrderResponseDTO> queryPaymentOrder(@PathVariable String payOrderNo) {
        try {
            return Response.success(toPaymentOrderResponse(paymentOrderService.queryPaymentOrder(payOrderNo)));
        } catch (IllegalArgumentException e) {
            return Response.fail("404", e.getMessage());
        }
    }

    private PaymentPrepareCommand toPrepareCommand(Long orderId, PaymentPrepareRequestDTO request) {
        PaymentPrepareCommand command = new PaymentPrepareCommand();
        command.setUserId(UserHolder.getUserId());
        command.setOrderId(orderId);
        command.setSource(readOrDefault(request == null ? null : request.getSource(), "FOOD_LIFE"));
        command.setChannel(readOrDefault(request == null ? null : request.getChannel(), "MOCK_PAY"));
        return command;
    }

    private PaymentCallbackCommand toCallbackCommand(PaymentCallbackRequestDTO request) {
        PaymentCallbackCommand command = new PaymentCallbackCommand();
        command.setPayOrderNo(request == null ? null : request.getPayOrderNo());
        command.setOutTradeNo(request == null ? null : request.getOutTradeNo());
        command.setPayAmount(request == null ? null : request.getPayAmount());
        command.setPayTime(request == null ? null : request.getPayTime());
        return command;
    }

    private PaymentCallbackResponseDTO toCallbackResponse(PaymentCallbackResult result) {
        PaymentCallbackResponseDTO response = new PaymentCallbackResponseDTO();
        response.setCallbackBehavior(result.getCallbackBehavior());
        response.setPaymentOrder(toPaymentOrderResponse(result.getPaymentOrder()));
        response.setSettlement(toPayResponse(result.getSettlement()));
        return response;
    }

    private PaymentOrderResponseDTO toPaymentOrderResponse(PaymentOrderEntity source) {
        PaymentOrderResponseDTO response = new PaymentOrderResponseDTO();
        response.setPayOrderNo(source.getPayOrderNo());
        response.setOrderId(source.getOrderId());
        response.setOrderNo(source.getOrderNo());
        response.setUserId(source.getUserId());
        response.setSource(source.getSource());
        response.setChannel(source.getChannel());
        response.setPayAmount(source.getPayAmount());
        response.setPayStatus(source.getPayStatus());
        response.setOutTradeNo(source.getOutTradeNo());
        response.setPayTime(source.getPayTime());
        response.setCreateTime(source.getCreateTime());
        response.setUpdateTime(source.getUpdateTime());
        return response;
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
        response.setTeamId(result.getTeamId());
        response.setActivityId(result.getActivityId());
        response.setTeamStatus(result.getTeamStatus());
        response.setTargetCount(result.getTargetCount());
        response.setLockCount(result.getLockCount());
        response.setCompleteCount(result.getCompleteCount());
        return response;
    }

    private String readOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}
