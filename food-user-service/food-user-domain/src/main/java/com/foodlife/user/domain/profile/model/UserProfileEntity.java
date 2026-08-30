package com.foodlife.user.domain.profile.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserProfileEntity implements Serializable {

    private Long userId;
    private String phone;
    private String nickName;
    private String icon;
    private String city;
    private String bio;
    private String foodPreference;
    private Long followingCount;
    private Long fansCount;
    private Boolean following;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
