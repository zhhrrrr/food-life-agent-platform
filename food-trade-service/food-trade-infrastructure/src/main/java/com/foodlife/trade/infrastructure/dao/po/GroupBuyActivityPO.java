package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_activity")
public class GroupBuyActivityPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long packageId;
    private String activityName;
    private Integer targetCount;
    private Integer userTakeLimit;
    private Long groupPrice;
    private Integer activityStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer stock;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
