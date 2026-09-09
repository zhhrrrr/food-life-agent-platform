package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("refund_order")
public class RefundOrderPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String refundOrderNo;
    private String payOrderNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String source;
    private String channel;
    private Long refundAmount;
    private String refundStatus;
    private String refundReason;
    private String outTradeNo;
    private String outRefundNo;
    private String failReason;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

