package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_coupon")
public class UserCouponPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long userId;
    private String couponName;
    private String couponType;
    private Long thresholdAmount;
    private Long discountAmount;
    private String scopeType;
    private Long scopeShopId;
    private Long scopePackageId;
    private String couponStatus;
    private Long usedOrderId;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
