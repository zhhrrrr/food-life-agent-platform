package com.foodlife.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentRouteConfiguration {

    @Bean
    public RouteLocator foodAgentRoute(
            RouteLocatorBuilder builder,
            @Value("${FOOD_AGENT_API_URL:http://127.0.0.1:8500}") String agentApiUrl) {
        return builder.routes()
                .route("food-agent-route", route -> route
                        .path("/api/agent/**")
                        .uri(agentApiUrl))
                .build();
    }
}
