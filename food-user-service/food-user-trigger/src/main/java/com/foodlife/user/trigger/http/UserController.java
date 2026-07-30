package com.foodlife.user.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.auth.model.LoginUserDTO;
import com.foodlife.user.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public Response<LoginUserDTO> me() {
        return Response.success(UserHolder.getUser());
    }
}
