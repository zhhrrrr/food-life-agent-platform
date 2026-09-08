package com.foodlife.trade.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String AUTHORIZATION = "authorization";

    @Bean
    public OpenAPI tradeServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Food Life Trade Service API")
                        .version("1.0")
                        .description("Normal purchase, group buy, seckill, payment, refund and verification APIs."))
                .components(new Components().addSecuritySchemes(AUTHORIZATION, bearerTokenScheme()))
                .addSecurityItem(new SecurityRequirement().addList(AUTHORIZATION));
    }

    private SecurityScheme bearerTokenScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("Redis Token");
    }
}
