package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("coupon_template")
public class CouponTemplatePO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String couponName;
    private String couponType;
    private Long thresholdAmount;
    private Long discountAmount;
    private String scopeType;
    private Long scopeShopId;
    private Long scopePackageId;
    private Integer userReceiveLimit;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer totalStock;
    private Integer receivedCount;
    private Integer templateStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
