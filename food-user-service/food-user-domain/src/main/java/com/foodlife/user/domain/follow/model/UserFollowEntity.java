package com.foodlife.user.domain.follow.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserFollowEntity implements Serializable {

    private Long id;
    private Long userId;
    private Long followUserId;
    private Integer followStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
