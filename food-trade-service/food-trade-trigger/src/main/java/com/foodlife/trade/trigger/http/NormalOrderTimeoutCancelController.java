package com.foodlife.trade.trigger.http;

import com.foodlife.trade.api.dto.NormalOrderTimeoutCancelResponseDTO;
import com.foodlife.trade.domain.order.normal.model.NormalOrderTimeoutCancelDetail;
import com.foodlife.trade.domain.order.normal.model.NormalOrderTimeoutCancelResult;
import com.foodlife.trade.domain.order.normal.service.NormalOrderTimeoutCancelService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade/normal/orders")
public class NormalOrderTimeoutCancelController {

    private final NormalOrderTimeoutCancelService normalOrderTimeoutCancelService;

    public NormalOrderTimeoutCancelController(NormalOrderTimeoutCancelService normalOrderTimeoutCancelService) {
        this.normalOrderTimeoutCancelService = normalOrderTimeoutCancelService;
    }

    @PostMapping("/timeout/cancel")
    public Response<NormalOrderTimeoutCancelResponseDTO> cancelTimeoutOrders(@RequestParam(required = false) Integer timeoutMinutes,
                                                                             @RequestParam(required = false) Integer limit) {
        NormalOrderTimeoutCancelResult result = normalOrderTimeoutCancelService.cancelTimeoutOrders(timeoutMinutes, limit);
        return Response.success(toResponse(result));
    }

    private NormalOrderTimeoutCancelResponseDTO toResponse(NormalOrderTimeoutCancelResult result) {
        NormalOrderTimeoutCancelResponseDTO response = new NormalOrderTimeoutCancelResponseDTO();
        response.setCompensateTime(result.getCompensateTime());
        response.setTimeoutMinutes(result.getTimeoutMinutes());
        response.setTimeoutBefore(result.getTimeoutBefore());
        response.setScannedOrderCount(result.getScannedOrderCount());
        response.setCanceledOrderCount(result.getCanceledOrderCount());
        response.setReleaseStockMessageCount(result.getReleaseStockMessageCount());
        response.setFailedOrderCount(result.getFailedOrderCount());
        response.setDetails(result.getDetails().stream().map(this::toDetail).collect(Collectors.toList()));
        return response;
    }

    private NormalOrderTimeoutCancelResponseDTO.Detail toDetail(NormalOrderTimeoutCancelDetail source) {
        NormalOrderTimeoutCancelResponseDTO.Detail detail = new NormalOrderTimeoutCancelResponseDTO.Detail();
        detail.setOrderId(source.getOrderId());
        detail.setOrderNo(source.getOrderNo());
        detail.setUserId(source.getUserId());
        detail.setPackageId(source.getPackageId());
        detail.setQuantity(source.getQuantity());
        detail.setBeforeOrderStatus(source.getBeforeOrderStatus());
        detail.setAfterOrderStatus(source.getAfterOrderStatus());
        detail.setCanceled(source.getCanceled());
        detail.setCouponReleased(source.getCouponReleased());
        detail.setReleaseStockMessageSent(source.getReleaseStockMessageSent());
        detail.setFailReason(source.getFailReason());
        return detail;
    }
}
