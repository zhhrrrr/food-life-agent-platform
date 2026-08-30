package com.foodlife.user.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FollowUserResponseDTO implements Serializable {

    private Long followId;
    private Long userId;
    private String nickName;
    private String icon;
    private LocalDateTime followTime;
}
