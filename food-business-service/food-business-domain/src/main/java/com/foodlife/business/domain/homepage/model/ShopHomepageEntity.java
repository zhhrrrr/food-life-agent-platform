package com.foodlife.business.domain.homepage.model;

import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.review.model.ShopReviewEntity;
import com.foodlife.business.domain.shop.model.ShopEntity;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShopHomepageEntity implements Serializable {

    private Long userId;
    private ShopEntity shop;
    private List<MealPackageEntity> packages = new ArrayList<>();
    private Boolean favorite;
    private Integer comments;
    private Integer score;
    private List<ShopReviewEntity> latestReviews = new ArrayList<>();
}
