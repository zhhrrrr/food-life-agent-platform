package com.foodlife.auth;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.auth.interceptor.RoleAccessInterceptor;
import com.foodlife.auth.model.LoginUserDTO;
import com.foodlife.auth.properties.AuthProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TradeMaintenanceAccessTest {
    @ParameterizedTest
    @ValueSource(strings={"food-trade-service/food-trade-app/src/main/resources/application-local.yml",
            "deploy/nacos/configs/food-trade-service.yaml"})
    void maintenanceRequiresOperatorWhileCustomerReadsRemainAvailable(String filename) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("trade",
                new FileSystemResource(Path.of("..", filename)))) environment.getPropertySources().addLast(source);
        AuthProperties properties = Binder.get(environment).bind("food.auth", Bindable.of(AuthProperties.class)).get();
        RoleAccessInterceptor interceptor = new RoleAccessInterceptor(properties);
        String[] maintenance = {"/api/trade/normal/orders/timeout/cancel", "/api/trade/pay/orders/timeout/close",
                "/api/trade/seckill/order-requests/process", "/api/trade/seckill/order-requests/recover",
                "/api/trade/seckill/activities/1/stock/reconcile", "/api/trade/seckill/activities/1/stock/preheat"};
        try {
            for (String role : new String[]{"USER", "ADMIN", "OPERATOR"}) {
                LoginUserDTO user = new LoginUserDTO(); user.setId(7L); user.setRole(role); UserHolder.saveUser(user);
                for (String path : maintenance) {
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    assertEquals(!role.equals("USER"), interceptor.preHandle(new MockHttpServletRequest("POST", path), response, new Object()), role + " " + path);
                    if (role.equals("USER")) assertEquals(403, response.getStatus());
                }
                assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/trade/seckill/activities/1"), new MockHttpServletResponse(), new Object()));
                assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/trade/orders/42"), new MockHttpServletResponse(), new Object()));
            }
        } finally { UserHolder.removeUser(); }
    }
}
