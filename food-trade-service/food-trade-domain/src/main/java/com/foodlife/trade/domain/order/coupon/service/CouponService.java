package com.foodlife.trade.domain.order.coupon.service;

import com.foodlife.trade.domain.order.coupon.constant.CouponStatusConstants;
import com.foodlife.trade.domain.order.coupon.constant.CouponTemplateStatusConstants;
import com.foodlife.trade.domain.order.coupon.model.CouponTemplateEntity;
import com.foodlife.trade.domain.order.coupon.model.UserCouponEntity;
import com.foodlife.trade.domain.order.coupon.repository.ICouponRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CouponService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ICouponRepository couponRepository;

    public CouponService(ICouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public List<CouponTemplateEntity> listAvailableTemplates(Integer limit) {
        return couponRepository.listAvailableTemplates(LocalDateTime.now(), normalizeLimit(limit));
    }

    public UserCouponEntity receiveCoupon(Long templateId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (templateId == null) {
            throw new IllegalArgumentException("templateId required");
        }

        LocalDateTime now = LocalDateTime.now();
        CouponTemplateEntity template = couponRepository.findTemplateById(templateId);
        validateTemplateCanReceive(template, now);
        if (!couponRepository.increaseReceivedCount(templateId)) {
            throw new IllegalArgumentException("coupon stock not enough");
        }

        UserCouponEntity userCoupon = new UserCouponEntity();
        userCoupon.setTemplateId(template.getId());
        userCoupon.setUserId(userId);
        userCoupon.setCouponName(template.getCouponName());
        userCoupon.setCouponType(template.getCouponType());
        userCoupon.setThresholdAmount(template.getThresholdAmount());
        userCoupon.setDiscountAmount(template.getDiscountAmount());
        userCoupon.setCouponStatus(CouponStatusConstants.UNUSED);
        userCoupon.setValidStartTime(template.getValidStartTime());
        userCoupon.setValidEndTime(template.getValidEndTime());
        userCoupon.setReceiveTime(now);
        userCoupon.setCreateTime(now);
        userCoupon.setUpdateTime(now);
        return couponRepository.saveUserCoupon(userCoupon);
    }

    public List<UserCouponEntity> listUserCoupons(Long userId, String couponStatus, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        return couponRepository.listUserCoupons(userId, trimToNull(couponStatus), normalizeLimit(limit));
    }

    public UserCouponEntity validateCouponForOrder(Long userCouponId, Long userId, Long orderAmount) {
        if (userCouponId == null) {
            return null;
        }
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        UserCouponEntity coupon = couponRepository.findUserCouponByIdAndUserId(userCouponId, userId);
        validateCouponCanUse(coupon, orderAmount, LocalDateTime.now());
        return coupon;
    }

    public void markCouponUsed(Long userCouponId, Long userId, Long orderId) {
        if (userCouponId == null) {
            return;
        }
        boolean success = couponRepository.markCouponUsed(userCouponId, userId, orderId, LocalDateTime.now());
        if (!success) {
            throw new IllegalArgumentException("coupon status can not use");
        }
    }

    public void releaseCoupon(Long userCouponId, Long userId, Long orderId) {
        if (userCouponId == null) {
            return;
        }
        couponRepository.releaseUsedCoupon(userCouponId, userId, orderId);
    }

    private void validateTemplateCanReceive(CouponTemplateEntity template, LocalDateTime now) {
        if (template == null) {
            throw new IllegalArgumentException("coupon template not found");
        }
        if (template.getTemplateStatus() == null || template.getTemplateStatus() != CouponTemplateStatusConstants.ENABLED) {
            throw new IllegalArgumentException("coupon template disabled");
        }
        if (template.getValidEndTime() != null && template.getValidEndTime().isBefore(now)) {
            throw new IllegalArgumentException("coupon template expired");
        }
        if (template.getTotalStock() != null && template.getReceivedCount() != null
                && template.getReceivedCount() >= template.getTotalStock()) {
            throw new IllegalArgumentException("coupon stock not enough");
        }
    }

    private void validateCouponCanUse(UserCouponEntity coupon, Long orderAmount, LocalDateTime now) {
        if (coupon == null) {
            throw new IllegalArgumentException("coupon not found");
        }
        if (!CouponStatusConstants.UNUSED.equals(coupon.getCouponStatus())) {
            throw new IllegalArgumentException("coupon status can not use");
        }
        if (coupon.getValidStartTime() != null && coupon.getValidStartTime().isAfter(now)) {
            throw new IllegalArgumentException("coupon not started");
        }
        if (coupon.getValidEndTime() != null && coupon.getValidEndTime().isBefore(now)) {
            throw new IllegalArgumentException("coupon expired");
        }
        if (orderAmount == null || orderAmount < coupon.getThresholdAmount()) {
            throw new IllegalArgumentException("coupon threshold not reached");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
