package com.foodlife.user.api;

import com.foodlife.user.api.dto.LoginRequestDTO;
import com.foodlife.user.api.dto.LoginResponseDTO;
import com.foodlife.user.types.response.Response;

public interface IUserAuthService {

    Response<Boolean> sendCode(String phone);

    Response<LoginResponseDTO> login(LoginRequestDTO request);

    Response<Boolean> logout(String token);
}
