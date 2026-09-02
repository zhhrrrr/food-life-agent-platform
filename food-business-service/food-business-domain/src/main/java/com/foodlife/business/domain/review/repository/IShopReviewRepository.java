package com.foodlife.business.domain.review.repository;

import com.foodlife.business.domain.review.model.ShopReviewEntity;

import java.util.List;

public interface IShopReviewRepository {

    boolean existsByOrderIdAndUserId(Long orderId, Long userId);

    ShopReviewEntity saveReview(ShopReviewEntity review);

    boolean applyReviewCreatedStats(String reviewNo, String messageId);

    ShopReviewEntity findActiveReviewByIdAndUserId(Long reviewId, Long userId);

    ShopReviewEntity hideReview(Long reviewId, Long userId);

    List<ShopReviewEntity> listByShopId(Long shopId, Long lastId, Integer limit);

    List<ShopReviewEntity> listByPackageId(Long packageId, Long lastId, Integer limit);

    List<ShopReviewEntity> listByUserId(Long userId, Long lastId, Integer limit);
}
