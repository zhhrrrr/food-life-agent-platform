package com.foodlife.auth.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginUserDTO implements Serializable {

    private Long id;
    private String nickName;
    private String icon;
}
