package com.foodlife.business.infrastructure.feign;

import com.foodlife.trade.api.dto.OrderDetailResponseDTO;
import com.foodlife.trade.types.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "food-trade-service", path = "/api/trade")
public interface TradeOrderClient {

    @GetMapping("/orders/{orderId}")
    Response<OrderDetailResponseDTO> queryOrderDetail(@PathVariable("orderId") Long orderId);
}
