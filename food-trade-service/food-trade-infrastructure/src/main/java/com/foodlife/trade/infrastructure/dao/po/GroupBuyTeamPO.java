package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_order")
public class GroupBuyTeamPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String teamId;
    private Long activityId;
    private Long packageId;
    private Integer targetCount;
    private Integer completeCount;
    private Integer lockCount;
    private String teamStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
