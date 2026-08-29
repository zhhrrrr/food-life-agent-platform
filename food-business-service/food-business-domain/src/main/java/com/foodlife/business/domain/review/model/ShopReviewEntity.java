package com.foodlife.business.domain.review.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopReviewEntity implements Serializable {

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
