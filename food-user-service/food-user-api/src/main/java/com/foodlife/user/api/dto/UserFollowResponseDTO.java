package com.foodlife.user.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserFollowResponseDTO implements Serializable {

    private Long followId;
    private Long userId;
    private Long followUserId;
    private Boolean following;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
