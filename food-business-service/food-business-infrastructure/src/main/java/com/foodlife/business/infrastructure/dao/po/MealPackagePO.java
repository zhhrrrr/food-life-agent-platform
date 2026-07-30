package com.foodlife.business.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("meal_package")
public class MealPackagePO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String name;
    private String description;
    private String coverImage;
    private Long price;
    private Long originalPrice;
    private Integer stock;
    private Integer sold;
    private Integer status;
    private String useRule;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
