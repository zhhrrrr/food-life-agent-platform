package com.foodlife.user.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_role")
public class UserRolePO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

