package com.foodlife.business.domain.review.repository;

import com.foodlife.business.domain.review.model.ShopReviewEntity;

import java.util.List;

public interface IShopReviewRepository {

    boolean existsByOrderIdAndUserId(Long orderId, Long userId);

    ShopReviewEntity saveReview(ShopReviewEntity review);

    List<ShopReviewEntity> listByShopId(Long shopId, Long lastId, Integer limit);

    List<ShopReviewEntity> listByUserId(Long userId, Long lastId, Integer limit);
}
