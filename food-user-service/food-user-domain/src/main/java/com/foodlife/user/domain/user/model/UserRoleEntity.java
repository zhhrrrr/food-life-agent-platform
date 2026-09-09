package com.foodlife.user.domain.user.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserRoleEntity implements Serializable {

    private Long id;
    private Long userId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

