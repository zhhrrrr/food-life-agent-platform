package com.foodlife.trade.domain.order.coupon.repository;

import com.foodlife.trade.domain.order.coupon.model.CouponTemplateEntity;
import com.foodlife.trade.domain.order.coupon.model.UserCouponEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface ICouponRepository {

    List<CouponTemplateEntity> listAvailableTemplates(LocalDateTime now, Integer limit);

    CouponTemplateEntity findTemplateById(Long templateId);

    boolean increaseReceivedCount(Long templateId);

    UserCouponEntity saveUserCoupon(UserCouponEntity userCoupon);

    UserCouponEntity findUserCouponByIdAndUserId(Long userCouponId, Long userId);

    List<UserCouponEntity> listUserCoupons(Long userId, String couponStatus, Integer limit);

    boolean markCouponUsed(Long userCouponId, Long userId, Long orderId, LocalDateTime useTime);

    boolean releaseUsedCoupon(Long userCouponId, Long userId, Long orderId);

    int expireUnusedCoupons(LocalDateTime now, Integer limit);

    int expireUserUnusedCoupons(Long userId, LocalDateTime now, Integer limit);
}
