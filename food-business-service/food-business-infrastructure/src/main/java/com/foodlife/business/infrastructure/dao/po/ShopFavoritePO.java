package com.foodlife.business.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("shop_favorite")
public class ShopFavoritePO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shopId;
    private Integer favoriteStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
