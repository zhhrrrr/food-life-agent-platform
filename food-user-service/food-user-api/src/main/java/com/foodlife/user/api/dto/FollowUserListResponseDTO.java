package com.foodlife.user.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class FollowUserListResponseDTO implements Serializable {

    private List<FollowUserResponseDTO> users = new ArrayList<>();
    private Boolean hasMore;
    private Long lastId;
}
