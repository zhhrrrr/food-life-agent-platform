package com.foodlife.user.domain.follow.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class FollowUserListResult implements Serializable {

    private List<FollowUserEntity> users = new ArrayList<>();
    private Boolean hasMore;
    private Long lastId;
}
