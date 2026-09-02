package com.foodlife.business.infrastructure.feign;

import com.foodlife.trade.api.dto.OrderDetailResponseDTO;
import com.foodlife.trade.types.response.Response;
import org.springframework.stereotype.Component;

@Component
public class TradeOrderClientFallback implements TradeOrderClient {

    @Override
    public Response<OrderDetailResponseDTO> queryOrderDetail(Long orderId) {
        return Response.fail("503", "订单服务暂不可用");
    }
}
