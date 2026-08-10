package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("seckill_order")
public class SeckillOrderPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long activityId;
    private Long packageId;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
