package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("trade_operation_stock_adjust_log")
public class OperationStockAdjustLogPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String operationId;
    private Long operatorId;
    private Long packageId;
    private Integer adjustQuantity;
    private String reason;
    private Integer stock;
    private Integer sold;
    private String txStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
