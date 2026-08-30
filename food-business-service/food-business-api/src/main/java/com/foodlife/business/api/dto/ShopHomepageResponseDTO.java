package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShopHomepageResponseDTO implements Serializable {

    private Long userId;
    private ShopInfoResponseDTO shop;
    private List<MealPackageResponseDTO> packages = new ArrayList<>();
    private Boolean favorite;
    private ShopReviewSummaryResponseDTO reviewSummary;
}
