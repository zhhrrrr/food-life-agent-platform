package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_order_list")
public class GroupBuyOrderListPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String teamId;
    private Long orderId;
    private String orderNo;
    private Long activityId;
    private Long packageId;
    private String orderStatus;
    private LocalDateTime outTradeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
