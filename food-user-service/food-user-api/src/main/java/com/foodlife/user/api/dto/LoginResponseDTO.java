package com.foodlife.user.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginResponseDTO implements Serializable {

    private String token;
}
