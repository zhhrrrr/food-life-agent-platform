package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("seckill_order_request")
public class SeckillOrderRequestPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String requestNo;
    private Long userId;
    private Long activityId;
    private Long packageId;
    private Integer quantity;
    private Long orderId;
    private String orderNo;
    private String requestStatus;
    private String failReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
