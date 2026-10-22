package com.foodlife.observability;

import com.foodlife.observability.filter.*;
import com.foodlife.observability.properties.ObservabilityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.*;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class TraceMetricCardinalityTest {
    @Test void customHttpVerbsCannotCreateUnlimitedMetricLabels() throws Exception {
        var registry = new SimpleMeterRegistry();
        var properties = new ObservabilityProperties(); properties.setRequestLogEnabled(false);
        var servlet = new ServletTraceFilter(properties, registry, new StandardEnvironment());
        var reactive = new ReactiveTraceWebFilter(properties, registry, new StandardEnvironment());
        for (int id=0; id<20; id++) {
            servlet.doFilter(new MockHttpServletRequest("CUSTOM_"+id, "/missing"), new MockHttpServletResponse(), (request,response) -> {});
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf("CUSTOM_"+id), "/missing"));
            reactive.filter(exchange, ignored -> Mono.empty()).block();
        }
        assertEquals(1, registry.find("food_http_server_request_duration").timers().size());
        assertNotNull(registry.find("food_http_server_request_duration").tag("method", "OTHER").timer());
    }
    @Test void servletIdsAndUnknownPathsDoNotCreateNewMeters() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new ServletTraceFilter(new ObservabilityProperties(), registry, new StandardEnvironment());
        for (int id=1; id<=20; id++) {
            filter.doFilter(new MockHttpServletRequest("GET", "/orders/"+id), new MockHttpServletResponse(), (request,response) ->
                    request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}"));
            filter.doFilter(new MockHttpServletRequest("GET", "/missing/"+id), new MockHttpServletResponse(), (request,response) ->
                    ((jakarta.servlet.http.HttpServletResponse)response).setStatus(404));
        }
        assertEquals(2, registry.find("food_http_server_request_duration").timers().size());
        assertNotNull(registry.find("food_http_server_request_duration").tag("path", "/orders/{id}").timer());
    }

    @Test void gatewayUsesMatchedPatternAndCoalescesUnknownPaths() {
        var registry = new SimpleMeterRegistry();
        var filter = new ReactiveTraceWebFilter(new ObservabilityProperties(), registry, new StandardEnvironment());
        for (int id=1; id<=20; id++) {
            var matched = MockServerWebExchange.from(MockServerHttpRequest.get("/orders/"+id));
            filter.filter(matched, exchange -> {
                exchange.getAttributes().put("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayPredicateMatchedPathAttr", "/orders/**");
                return Mono.empty();
            }).block();
            var missing = MockServerWebExchange.from(MockServerHttpRequest.get("/missing/"+id));
            filter.filter(missing, exchange -> { exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND); return Mono.empty(); }).block();
        }
        assertEquals(2, registry.find("food_http_server_request_duration").timers().size());
        assertNotNull(registry.find("food_http_server_request_duration").tag("path", "/orders/**").timer());
    }
}
