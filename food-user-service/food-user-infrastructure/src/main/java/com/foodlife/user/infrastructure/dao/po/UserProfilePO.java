package com.foodlife.user.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfilePO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String city;
    private String bio;
    private String foodPreference;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
