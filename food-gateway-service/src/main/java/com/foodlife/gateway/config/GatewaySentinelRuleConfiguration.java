package com.foodlife.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.foodlife.gateway.properties.GatewaySentinelProperties;
import com.foodlife.gateway.support.GatewayErrorResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class GatewaySentinelRuleConfiguration {

    private static final String API_TRADE_ORDER_CREATE = "gateway_api_trade_order_create";
    private static final String API_SECKILL_ORDER_CREATE = "gateway_api_seckill_order_create";
    private static final String API_PAYMENT_CALLBACK = "gateway_api_payment_callback";
    private static final String API_SMOKE = "gateway_api_sentinel_smoke";

    private final GatewaySentinelProperties properties;

    public GatewaySentinelRuleConfiguration(GatewaySentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initGatewayRules() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return;
        }
        GatewayApiDefinitionManager.loadApiDefinitions(buildApiDefinitions());
        GatewayRuleManager.loadRules(buildGatewayFlowRules());
        GatewayCallbackManager.setBlockHandler(new JsonBlockRequestHandler());
    }

    private Set<ApiDefinition> buildApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();
        definitions.add(api(API_TRADE_ORDER_CREATE,
                "/api/trade/orders/normal",
                "/api/trade/orders/group-buy"));
        definitions.add(api(API_SECKILL_ORDER_CREATE,
                "/api/trade/orders/seckill",
                "/api/trade/orders/seckill/async"));
        definitions.add(api(API_PAYMENT_CALLBACK,
                "/api/trade/pay/callback/local",
                "/api/trade/pay/callback/mock"));
        definitions.add(api(API_SMOKE, "/api/shop-category/list"));
        return definitions;
    }

    private ApiDefinition api(String apiName, String... paths) {
        Set<ApiPredicateItem> predicateItems = new HashSet<>();
        for (String path : paths) {
            predicateItems.add(new ApiPathPredicateItem()
                    .setPattern(path)
                    .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
        }
        return new ApiDefinition(apiName).setPredicateItems(predicateItems);
    }

    private Set<GatewayFlowRule> buildGatewayFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(apiQpsRule(API_TRADE_ORDER_CREATE, properties.getTradeOrderCreateQps()));
        rules.add(apiQpsRule(API_SECKILL_ORDER_CREATE, properties.getSeckillOrderCreateQps()));
        rules.add(apiQpsRule(API_PAYMENT_CALLBACK, properties.getPaymentCallbackQps()));
        rules.add(routeQpsRule("food-trade-route", properties.getTradeRouteQps()));
        rules.add(routeQpsRule("food-business-route", properties.getBusinessRouteQps()));
        rules.add(routeQpsRule("food-user-route", properties.getUserRouteQps()));
        rules.add(headerQpsRule(API_TRADE_ORDER_CREATE, properties.getUserHeaderName(), properties.getUserHeaderQps()));

        if (Boolean.TRUE.equals(properties.getSmokeRuleEnabled())) {
            rules.add(headerQpsRule(API_SMOKE, "X-Sentinel-Smoke", 1D).setIntervalSec(60));
        }
        return rules;
    }

    private GatewayFlowRule apiQpsRule(String apiName, Double qps) {
        return new GatewayFlowRule(apiName)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(qps)
                .setIntervalSec(1);
    }

    private GatewayFlowRule routeQpsRule(String routeId, Double qps) {
        return new GatewayFlowRule(routeId)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(qps)
                .setIntervalSec(1);
    }

    private GatewayFlowRule headerQpsRule(String apiName, String headerName, Double qps) {
        return apiQpsRule(apiName, qps)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                        .setFieldName(headerName));
    }

    private static class JsonBlockRequestHandler implements BlockRequestHandler {

        @Override
        public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable throwable) {
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(GatewayErrorResponse.body("429", "service busy, please try again later"));
        }
    }
}
