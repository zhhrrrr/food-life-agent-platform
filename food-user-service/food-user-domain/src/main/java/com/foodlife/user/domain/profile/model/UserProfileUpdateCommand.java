package com.foodlife.user.domain.profile.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserProfileUpdateCommand implements Serializable {

    private Long userId;
    private String nickName;
    private String icon;
    private String city;
    private String bio;
    private String foodPreference;
}
