package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeleteShopReviewResponseDTO implements Serializable {

    private Long reviewId;
    private Long userId;
    private Long shopId;
    private Long packageId;
    private Long orderId;
    private Integer reviewStatus;
    private Boolean deleted;
}
