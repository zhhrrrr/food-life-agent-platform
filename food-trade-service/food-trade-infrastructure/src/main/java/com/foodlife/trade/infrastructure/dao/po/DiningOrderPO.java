package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("dining_order")
public class DiningOrderPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private Integer quantity;
    private Long totalAmount;
    private Long payAmount;
    private String tradeType;
    private String orderStatus;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
