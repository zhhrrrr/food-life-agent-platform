package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShopReviewSummaryResponseDTO implements Serializable {

    private Long shopId;
    private Integer comments;
    private Integer score;
    private List<ShopReviewResponseDTO> latestReviews = new ArrayList<>();
}
