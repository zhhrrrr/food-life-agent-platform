package com.foodlife.trade.trigger.http;

import com.foodlife.trade.api.dto.PaymentReconcileResponseDTO;
import com.foodlife.trade.domain.order.payment.model.PaymentReconcileResult;
import com.foodlife.trade.domain.order.payment.service.PaymentReconcileService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade/operations/payments")
public class OperationPaymentController {

    private final PaymentReconcileService paymentReconcileService;

    public OperationPaymentController(PaymentReconcileService paymentReconcileService) {
        this.paymentReconcileService = paymentReconcileService;
    }

    @PostMapping("/reconcile")
    public Response<PaymentReconcileResponseDTO> reconcile(@RequestParam(required = false) Integer limit) {
        return Response.success(toResponse(paymentReconcileService.reconcile(limit)));
    }

    private PaymentReconcileResponseDTO toResponse(PaymentReconcileResult result) {
        PaymentReconcileResponseDTO response = new PaymentReconcileResponseDTO();
        response.setScannedPaymentCount(result.getScannedPaymentCount());
        response.setPreparedPaymentCount(result.getPreparedPaymentCount());
        response.setSuccessPaymentCount(result.getSuccessPaymentCount());
        response.setClosedPaymentCount(result.getClosedPaymentCount());
        response.setRefundedPaymentCount(result.getRefundedPaymentCount());
        response.setScannedRefundCount(result.getScannedRefundCount());
        response.setPreparedRefundCount(result.getPreparedRefundCount());
        response.setSuccessRefundCount(result.getSuccessRefundCount());
        response.setFailedRefundCount(result.getFailedRefundCount());
        response.setInconsistentPayOrderNos(result.getInconsistentPayOrderNos());
        return response;
    }
}

