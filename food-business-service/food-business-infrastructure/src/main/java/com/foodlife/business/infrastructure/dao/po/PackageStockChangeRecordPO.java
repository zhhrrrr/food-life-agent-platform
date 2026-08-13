package com.foodlife.business.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("package_stock_change_record")
public class PackageStockChangeRecordPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String operationId;
    private Long packageId;
    private Integer quantity;
    private String changeType;
    private String changeStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
