package com.foodlife.user.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserFollowStatusResponseDTO implements Serializable {

    private Long userId;
    private Long followUserId;
    private Boolean following;
}
