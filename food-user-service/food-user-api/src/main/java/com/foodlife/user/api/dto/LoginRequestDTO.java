package com.foodlife.user.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginRequestDTO implements Serializable {

    private String phone;
    private String code;
}
