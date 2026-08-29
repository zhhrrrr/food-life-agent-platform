package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopReviewResponseDTO implements Serializable {

    private Long reviewId;
    private String reviewNo;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private Long orderId;
    private String orderNo;
    private Integer score;
    private String content;
    private String images;
    private LocalDateTime createTime;
}
