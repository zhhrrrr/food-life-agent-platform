package com.foodlife.business.infrastructure.port;

import com.foodlife.business.domain.review.model.TradeOrderForReviewEntity;
import com.foodlife.business.domain.review.port.ITradeOrderPort;
import com.foodlife.business.types.response.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class TradeOrderPort implements ITradeOrderPort {

    private final RestTemplate restTemplate;
    private final String tradeServiceBaseUrl;
    private final String tokenHeader;

    public TradeOrderPort(RestTemplate restTemplate,
                          @Value("${food.trade-service.base-url}") String tradeServiceBaseUrl,
                          @Value("${food.auth.token-header:authorization}") String tokenHeader) {
        this.restTemplate = restTemplate;
        this.tradeServiceBaseUrl = tradeServiceBaseUrl;
        this.tokenHeader = tokenHeader;
    }

    @Override
    public TradeOrderForReviewEntity queryCurrentUserOrder(Long orderId) {
        ResponseEntity<Response> responseEntity;
        try {
            responseEntity = restTemplate.exchange(
                    tradeServiceBaseUrl + "/api/trade/orders/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Response.class
            );
        } catch (RestClientException e) {
            return null;
        }
        Response response = responseEntity.getBody();
        if (response == null || !"0000".equals(response.getCode()) || response.getData() == null) {
            return null;
        }
        return toEntity(response.getData());
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
            String token = request.getHeader(tokenHeader);
            if (token != null && token.length() > 0) {
                headers.add(tokenHeader, token);
            }
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private TradeOrderForReviewEntity toEntity(Object data) {
        Map<String, Object> map = (Map<String, Object>) data;
        TradeOrderForReviewEntity entity = new TradeOrderForReviewEntity();
        entity.setOrderId(toLong(map.get("orderId")));
        entity.setOrderNo((String) map.get("orderNo"));
        entity.setUserId(toLong(map.get("userId")));
        entity.setShopId(toLong(map.get("shopId")));
        entity.setPackageId(toLong(map.get("packageId")));
        entity.setQuantity(toInteger(map.get("quantity")));
        entity.setTradeType((String) map.get("tradeType"));
        entity.setOrderStatus((String) map.get("orderStatus"));
        entity.setUseTime(toLocalDateTime(map.get("useTime")));
        entity.setCreateTime(toLocalDateTime(map.get("createTime")));
        return entity;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<?> items = (List<?>) value;
            return LocalDateTime.of(toInt(items.get(0)), toInt(items.get(1)), toInt(items.get(2)),
                    toInt(items.get(3)), toInt(items.get(4)), toInt(items.get(5)));
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    private int toInt(Object value) {
        return Integer.parseInt(String.valueOf(value));
    }
}
