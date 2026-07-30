package com.foodlife.business.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("shop")
public class ShopPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String name;
    private Long categoryId;
    private String images;
    private String area;
    private String address;
    private Double longitude;
    private Double latitude;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
