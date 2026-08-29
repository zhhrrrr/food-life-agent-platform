package com.foodlife.business.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("shop_review")
public class ShopReviewPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String reviewNo;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private Long orderId;
    private String orderNo;
    private Integer score;
    private String content;
    private String images;
    private Integer reviewStatus;
    private Integer shopCommentsBefore;
    private Integer shopScoreBefore;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
