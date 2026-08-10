package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("trade_local_message")
public class TradeLocalMessagePO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String messageType;
    private String bizType;
    private String bizId;
    private String messageStatus;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextRetryTime;
    private String content;
    private String failReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
