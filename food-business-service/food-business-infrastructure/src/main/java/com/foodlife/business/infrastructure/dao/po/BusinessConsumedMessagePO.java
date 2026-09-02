package com.foodlife.business.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("business_consumed_message")
public class BusinessConsumedMessagePO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String topic;
    private String tag;
    private String bizKey;
    private String consumeStatus;
    private String failReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

