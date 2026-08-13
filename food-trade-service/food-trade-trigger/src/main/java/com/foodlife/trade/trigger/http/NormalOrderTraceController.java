package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.NormalOrderTraceResponseDTO;
import com.foodlife.trade.api.dto.OrderItemResponseDTO;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.normal.model.NormalOrderTraceEntity;
import com.foodlife.trade.domain.order.normal.model.PackageStockChangeRecord;
import com.foodlife.trade.domain.order.normal.service.NormalOrderTraceService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade")
public class NormalOrderTraceController {

    private final NormalOrderTraceService normalOrderTraceService;

    public NormalOrderTraceController(NormalOrderTraceService normalOrderTraceService) {
        this.normalOrderTraceService = normalOrderTraceService;
    }

    @GetMapping("/orders/{orderId}/normal-trace")
    public Response<NormalOrderTraceResponseDTO> queryNormalOrderTrace(@PathVariable Long orderId) {
        try {
            NormalOrderTraceEntity trace = normalOrderTraceService.queryTrace(orderId, UserHolder.getUserId());
            return Response.success(toResponse(trace));
        } catch (IllegalArgumentException e) {
            return Response.fail("404", e.getMessage());
        }
    }

    private NormalOrderTraceResponseDTO toResponse(NormalOrderTraceEntity trace) {
        NormalOrderTraceResponseDTO response = new NormalOrderTraceResponseDTO();
        response.setOrder(toOrderInfo(trace.getOrder(), trace.getOrderItems()));
        response.setPackageInfo(toPackageInfo(trace.getPackageSnapshot()));
        response.setStockMessages(trace.getStockMessages().stream().map(this::toStockMessageInfo).collect(Collectors.toList()));
        response.setStockChangeRecords(trace.getStockChangeRecords().stream().map(this::toStockChangeRecordInfo).collect(Collectors.toList()));
        response.setCurrentStage(trace.getCurrentStage());
        return response;
    }

    private NormalOrderTraceResponseDTO.OrderInfo toOrderInfo(DiningOrderEntity order, List<DiningOrderItemEntity> items) {
        NormalOrderTraceResponseDTO.OrderInfo info = new NormalOrderTraceResponseDTO.OrderInfo();
        info.setOrderId(order.getId());
        info.setOrderNo(order.getOrderNo());
        info.setUserId(order.getUserId());
        info.setShopId(order.getShopId());
        info.setPackageId(order.getPackageId());
        info.setQuantity(order.getQuantity());
        info.setTotalAmount(order.getTotalAmount());
        info.setPayAmount(order.getPayAmount());
        info.setTradeType(order.getTradeType());
        info.setOrderStatus(order.getOrderStatus());
        info.setUseTime(order.getUseTime());
        info.setCreateTime(order.getCreateTime());
        info.setItems(items.stream().map(this::toItemResponse).collect(Collectors.toList()));
        return info;
    }

    private NormalOrderTraceResponseDTO.PackageInfo toPackageInfo(PackageTradeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        NormalOrderTraceResponseDTO.PackageInfo info = new NormalOrderTraceResponseDTO.PackageInfo();
        info.setShopId(snapshot.getShopId());
        info.setShopName(snapshot.getShopName());
        info.setPackageId(snapshot.getPackageId());
        info.setPackageName(snapshot.getPackageName());
        info.setPackageDescription(snapshot.getPackageDescription());
        info.setCoverImage(snapshot.getCoverImage());
        info.setPrice(snapshot.getPrice());
        info.setOriginalPrice(snapshot.getOriginalPrice());
        info.setStock(snapshot.getStock());
        info.setPackageStatus(snapshot.getPackageStatus());
        info.setUseRule(snapshot.getUseRule());
        return info;
    }

    private NormalOrderTraceResponseDTO.StockMessageInfo toStockMessageInfo(TradeLocalMessageEntity source) {
        NormalOrderTraceResponseDTO.StockMessageInfo info = new NormalOrderTraceResponseDTO.StockMessageInfo();
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

    private NormalOrderTraceResponseDTO.StockChangeRecordInfo toStockChangeRecordInfo(PackageStockChangeRecord source) {
        NormalOrderTraceResponseDTO.StockChangeRecordInfo info = new NormalOrderTraceResponseDTO.StockChangeRecordInfo();
        info.setId(source.getId());
        info.setOperationId(source.getOperationId());
        info.setPackageId(source.getPackageId());
        info.setQuantity(source.getQuantity());
        info.setChangeType(source.getChangeType());
        info.setChangeStatus(source.getChangeStatus());
        info.setCreateTime(source.getCreateTime());
        info.setUpdateTime(source.getUpdateTime());
        return info;
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
