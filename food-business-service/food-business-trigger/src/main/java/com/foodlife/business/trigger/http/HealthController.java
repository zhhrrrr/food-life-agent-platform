package com.foodlife.business.trigger.http;

import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Response<String> health() {
        return Response.success("food-business-service ok");
    }
}
