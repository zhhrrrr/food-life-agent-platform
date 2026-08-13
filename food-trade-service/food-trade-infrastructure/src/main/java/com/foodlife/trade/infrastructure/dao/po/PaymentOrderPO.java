package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("payment_order")
public class PaymentOrderPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String payOrderNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String source;
    private String channel;
    private Long payAmount;
    private String payStatus;
    private String outTradeNo;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
