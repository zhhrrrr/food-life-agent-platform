package com.foodlife.business.infrastructure.port;

import com.foodlife.business.domain.review.model.TradeOrderForReviewEntity;
import com.foodlife.business.domain.review.port.ITradeOrderPort;
import com.foodlife.business.infrastructure.feign.TradeOrderClient;
import com.foodlife.trade.api.dto.OrderDetailResponseDTO;
import feign.FeignException;
import org.springframework.stereotype.Component;

@Component
public class TradeOrderPort implements ITradeOrderPort {

    private final TradeOrderClient tradeOrderClient;

    public TradeOrderPort(TradeOrderClient tradeOrderClient) {
        this.tradeOrderClient = tradeOrderClient;
    }

    @Override
    public TradeOrderForReviewEntity queryCurrentUserOrder(Long orderId) {
        com.foodlife.trade.types.response.Response<OrderDetailResponseDTO> response;
        try {
            response = tradeOrderClient.queryOrderDetail(orderId);
        } catch (FeignException e) {
            return null;
        }
        if (response == null || !"0000".equals(response.getCode()) || response.getData() == null) {
            return null;
        }
        return toEntity(response.getData());
    }

    private TradeOrderForReviewEntity toEntity(OrderDetailResponseDTO data) {
        TradeOrderForReviewEntity entity = new TradeOrderForReviewEntity();
        entity.setOrderId(data.getOrderId());
        entity.setOrderNo(data.getOrderNo());
        entity.setUserId(data.getUserId());
        entity.setShopId(data.getShopId());
        entity.setPackageId(data.getPackageId());
        entity.setQuantity(data.getQuantity());
        entity.setTradeType(data.getTradeType());
        entity.setOrderStatus(data.getOrderStatus());
        entity.setUseTime(data.getUseTime());
        entity.setCreateTime(data.getCreateTime());
        return entity;
    }
}
