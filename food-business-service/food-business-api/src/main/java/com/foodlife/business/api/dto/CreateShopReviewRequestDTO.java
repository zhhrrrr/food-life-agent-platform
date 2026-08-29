package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateShopReviewRequestDTO implements Serializable {

    private Long orderId;
    private Integer score;
    private String content;
    private String images;
}
