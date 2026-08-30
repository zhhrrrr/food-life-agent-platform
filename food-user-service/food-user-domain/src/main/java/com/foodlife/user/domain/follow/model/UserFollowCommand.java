package com.foodlife.user.domain.follow.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserFollowCommand implements Serializable {

    private Long userId;
    private Long followUserId;
}
