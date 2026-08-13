package com.foodlife.trade.trigger.http;

import com.foodlife.trade.api.dto.NormalPackageStockSyncResponseDTO;
import com.foodlife.trade.domain.order.normal.model.NormalPackageStockSyncResult;
import com.foodlife.trade.domain.order.normal.service.NormalPackageStockMessageService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade/normal/package-stock")
public class NormalPackageStockSyncController {

    private final NormalPackageStockMessageService normalPackageStockMessageService;

    public NormalPackageStockSyncController(NormalPackageStockMessageService normalPackageStockMessageService) {
        this.normalPackageStockMessageService = normalPackageStockMessageService;
    }

    @PostMapping("/compensate")
    public Response<NormalPackageStockSyncResponseDTO> compensate(@RequestParam(required = false) Integer limit) {
        NormalPackageStockSyncResult result = normalPackageStockMessageService.compensatePendingMessages(limit);
        return Response.success(toResponse(result));
    }

    private NormalPackageStockSyncResponseDTO toResponse(NormalPackageStockSyncResult result) {
        NormalPackageStockSyncResponseDTO response = new NormalPackageStockSyncResponseDTO();
        response.setCompensateTime(result.getCompensateTime());
        response.setScannedMessageCount(result.getScannedMessageCount());
        response.setSuccessCount(result.getSuccessCount());
        response.setRetryCount(result.getRetryCount());
        response.setFailedCount(result.getFailedCount());
        return response;
    }
}
