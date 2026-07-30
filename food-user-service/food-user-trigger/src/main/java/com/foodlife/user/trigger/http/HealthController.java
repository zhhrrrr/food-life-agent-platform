package com.foodlife.user.trigger.http;

import com.foodlife.user.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Response<String> health() {
        return Response.success("food-user-service ok");
    }
}
