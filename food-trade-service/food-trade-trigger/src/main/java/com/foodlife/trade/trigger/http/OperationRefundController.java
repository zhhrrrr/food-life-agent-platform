package com.foodlife.trade.trigger.http;

import com.foodlife.trade.domain.order.payment.model.RefundOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IRefundOrderRepository;
import com.foodlife.trade.domain.order.payment.service.RefundRecoveryService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Covered by the existing /api/trade/operations/** role access rule. */
@RestController
@RequestMapping("/api/trade/operations/refunds")
public class OperationRefundController {
    private final IRefundOrderRepository refunds;
    private final RefundRecoveryService recovery;

    public OperationRefundController(IRefundOrderRepository refunds, RefundRecoveryService recovery) {
        this.refunds = refunds;
        this.recovery = recovery;
    }

    @GetMapping
    public Response<List<RefundOrderEntity>> list(@RequestParam(required = false) Integer limit) {
        return Response.success(refunds.listRecentRefundOrders(limit));
    }

    @PostMapping("/recover")
    public Response<Integer> recover(@RequestParam(required = false) Integer limit) {
        return Response.success(recovery.recover(limit));
    }

    @PostMapping("/{refundOrderNo}/retry")
    public Response<Boolean> retry(@PathVariable String refundOrderNo) {
        return Response.success(recovery.retry(refundOrderNo));
    }
}
