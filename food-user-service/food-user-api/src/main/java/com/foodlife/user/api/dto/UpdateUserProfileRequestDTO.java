package com.foodlife.user.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateUserProfileRequestDTO implements Serializable {

    private String nickName;
    private String icon;
    private String city;
    private String bio;
    private String foodPreference;
}
