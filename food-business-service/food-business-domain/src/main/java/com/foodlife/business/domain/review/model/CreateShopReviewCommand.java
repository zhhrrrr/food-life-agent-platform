package com.foodlife.business.domain.review.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateShopReviewCommand implements Serializable {

    private Long userId;
    private Long orderId;
    private Integer score;
    private String content;
    private String images;
}
