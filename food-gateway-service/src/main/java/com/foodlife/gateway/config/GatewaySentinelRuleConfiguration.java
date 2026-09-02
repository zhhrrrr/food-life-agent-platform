package com.foodlife.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.foodlife.gateway.properties.GatewaySentinelProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
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

    @Bean
    public BlockRequestHandler gatewaySentinelBlockRequestHandler() {
        return new JsonBlockRequestHandler();
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
                "/api/trade/pay/callback/mock"));
        definitions.add(api(API_SMOKE, "/api/shop-category/list"));
        return definitions;
    }

    private ApiDefinition api(String apiName, String... paths) {
        Set<com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem> predicateItems = new HashSet<>();
        for (String path : paths) {
            predicateItems.add(new ApiPathPredicateItem()
                    .setPattern(path)
                    .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
        }
        return new ApiDefinition(apiName).setPredicateItems(predicateItems);
    }

    private Set<GatewayFlowRule> buildGatewayFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(new GatewayFlowRule(API_TRADE_ORDER_CREATE)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(properties.getTradeOrderCreateQps())
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule(API_SECKILL_ORDER_CREATE)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(properties.getSeckillOrderCreateQps())
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule(API_PAYMENT_CALLBACK)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(properties.getPaymentCallbackQps())
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule("food-trade-route")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(properties.getTradeRouteQps())
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule("food-business-route")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(properties.getBusinessRouteQps())
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule("food-user-route")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(properties.getUserRouteQps())
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule(API_TRADE_ORDER_CREATE)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(properties.getUserHeaderQps())
                .setIntervalSec(1)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                        .setFieldName(properties.getUserHeaderName())));
        if (Boolean.TRUE.equals(properties.getSmokeRuleEnabled())) {
            rules.add(new GatewayFlowRule(API_SMOKE)
                    .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                    .setCount(1)
                    .setIntervalSec(60)
                    .setParamItem(new GatewayParamFlowItem()
                            .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                            .setFieldName("X-Sentinel-Smoke")));
        }
        return rules;
    }

    private static class JsonBlockRequestHandler implements BlockRequestHandler {

        private static final String BODY = "{\"code\":\"429\",\"message\":\"service busy, please try again later\"}";

        @Override
        public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable throwable) {
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(BODY);
        }
    }
}
