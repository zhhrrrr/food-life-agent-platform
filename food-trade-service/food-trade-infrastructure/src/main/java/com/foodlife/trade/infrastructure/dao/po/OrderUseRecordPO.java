package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("order_use_record")
public class OrderUseRecordPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String useRecordNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private String tradeType;
    private String useSource;
    private String useStatus;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
