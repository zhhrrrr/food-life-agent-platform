package com.foodlife.trade.trigger.http;

import com.foodlife.trade.api.dto.NormalPackageStockMessageListResponseDTO;
import com.foodlife.trade.api.dto.NormalPackageStockSyncResponseDTO;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.normal.model.NormalPackageStockSyncResult;
import com.foodlife.trade.domain.order.normal.service.NormalPackageStockMessageService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

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

    @GetMapping("/messages")
    public Response<NormalPackageStockMessageListResponseDTO> queryMessages(@RequestParam(required = false) Long orderId,
                                                                            @RequestParam(required = false) String messageStatus,
                                                                            @RequestParam(required = false) Integer limit) {
        NormalPackageStockMessageListResponseDTO response = new NormalPackageStockMessageListResponseDTO();
        response.setMessages(normalPackageStockMessageService.queryMessages(orderId, messageStatus, limit)
                .stream()
                .map(this::toMessageInfo)
                .collect(Collectors.toList()));
        return Response.success(response);
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

    private NormalPackageStockMessageListResponseDTO.MessageInfo toMessageInfo(TradeLocalMessageEntity source) {
        NormalPackageStockMessageListResponseDTO.MessageInfo info = new NormalPackageStockMessageListResponseDTO.MessageInfo();
        info.setId(source.getId());
        info.setMessageId(source.getMessageId());
        info.setMessageType(source.getMessageType());
        info.setBizType(source.getBizType());
        info.setBizId(source.getBizId());
        info.setMessageStatus(source.getMessageStatus());
        info.setRetryCount(source.getRetryCount());
        info.setMaxRetryCount(source.getMaxRetryCount());
        info.setNextRetryTime(source.getNextRetryTime());
        info.setContent(source.getContent());
        info.setFailReason(source.getFailReason());
        info.setCreateTime(source.getCreateTime());
        info.setUpdateTime(source.getUpdateTime());
        return info;
    }
}
