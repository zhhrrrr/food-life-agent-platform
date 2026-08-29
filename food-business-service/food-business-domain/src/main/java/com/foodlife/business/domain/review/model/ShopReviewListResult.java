package com.foodlife.business.domain.review.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShopReviewListResult implements Serializable {

    private List<ShopReviewEntity> reviews = new ArrayList<>();
    private Boolean hasMore;
    private Long lastId;
}
