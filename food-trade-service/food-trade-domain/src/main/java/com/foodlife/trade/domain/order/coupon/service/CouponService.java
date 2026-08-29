package com.foodlife.trade.domain.order.coupon.service;

import com.foodlife.trade.domain.order.coupon.constant.CouponStatusConstants;
import com.foodlife.trade.domain.order.coupon.constant.CouponScopeConstants;
import com.foodlife.trade.domain.order.coupon.constant.CouponTemplateStatusConstants;
import com.foodlife.trade.domain.order.coupon.model.CouponExpireScanResult;
import com.foodlife.trade.domain.order.coupon.model.CouponReleaseResult;
import com.foodlife.trade.domain.order.coupon.model.CouponTemplateEntity;
import com.foodlife.trade.domain.order.coupon.model.UserCouponEntity;
import com.foodlife.trade.domain.order.coupon.repository.ICouponRepository;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CouponService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_EXPIRE_LIMIT = 500;
    private static final int MAX_EXPIRE_LIMIT = 2000;

    private final ICouponRepository couponRepository;

    public CouponService(ICouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public List<CouponTemplateEntity> listAvailableTemplates(Integer limit) {
        return couponRepository.listAvailableTemplates(LocalDateTime.now(), normalizeLimit(limit));
    }

    @Transactional(rollbackFor = Exception.class)
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
        validateUserReceiveLimit(template, userId);
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
        userCoupon.setScopeType(normalizeScopeType(template.getScopeType()));
        userCoupon.setScopeShopId(template.getScopeShopId());
        userCoupon.setScopePackageId(template.getScopePackageId());
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
        couponRepository.expireUserUnusedCoupons(userId, LocalDateTime.now(), normalizeLimit(limit));
        return couponRepository.listUserCoupons(userId, trimToNull(couponStatus), normalizeLimit(limit));
    }

    public UserCouponEntity validateCouponForOrder(Long userCouponId, Long userId, Long orderAmount, PackageTradeSnapshot packageSnapshot) {
        if (userCouponId == null) {
            return null;
        }
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        UserCouponEntity coupon = couponRepository.findUserCouponByIdAndUserId(userCouponId, userId);
        validateCouponCanUse(coupon, orderAmount, packageSnapshot, LocalDateTime.now());
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

    public boolean releaseCoupon(Long userCouponId, Long userId, Long orderId) {
        return releaseCouponWithResult(userCouponId, userId, orderId).getReleased();
    }

    public CouponReleaseResult releaseCouponWithResult(Long userCouponId, Long userId, Long orderId) {
        CouponReleaseResult result = new CouponReleaseResult();
        result.setUserCouponId(userCouponId);
        if (userCouponId == null) {
            result.setReleased(true);
            return result;
        }
        if (userId == null || orderId == null) {
            result.setReleased(false);
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        UserCouponEntity coupon = couponRepository.findUserCouponByIdAndUserId(userCouponId, userId);
        String releaseStatus = resolveReleaseStatus(coupon, now);
        boolean released = couponRepository.releaseUsedCoupon(userCouponId, userId, orderId, releaseStatus);
        result.setReleased(released);
        result.setCouponStatus(released ? releaseStatus : coupon == null ? null : coupon.getCouponStatus());
        return result;
    }

    public CouponExpireScanResult expireUnusedCoupons(Integer limit) {
        LocalDateTime now = LocalDateTime.now();
        int normalizedLimit = normalizeExpireLimit(limit);
        int expiredCount = couponRepository.expireUnusedCoupons(now, normalizedLimit);

        CouponExpireScanResult result = new CouponExpireScanResult();
        result.setScanTime(now);
        result.setExpireBefore(now);
        result.setExpiredCount(expiredCount);
        result.setLimit(normalizedLimit);
        return result;
    }

    private void validateTemplateCanReceive(CouponTemplateEntity template, LocalDateTime now) {
        if (template == null) {
            throw new IllegalArgumentException("coupon template not found");
        }
        if (template.getTemplateStatus() == null || template.getTemplateStatus() != CouponTemplateStatusConstants.ENABLED) {
            throw new IllegalArgumentException("coupon template disabled");
        }
        if (template.getValidStartTime() != null && template.getValidStartTime().isAfter(now)) {
            throw new IllegalArgumentException("coupon template not started");
        }
        if (template.getValidEndTime() != null && template.getValidEndTime().isBefore(now)) {
            throw new IllegalArgumentException("coupon template expired");
        }
        if (template.getTotalStock() != null && template.getReceivedCount() != null
                && template.getReceivedCount() >= template.getTotalStock()) {
            throw new IllegalArgumentException("coupon stock not enough");
        }
        validateScopeConfig(template.getScopeType(), template.getScopeShopId(), template.getScopePackageId());
    }

    private void validateUserReceiveLimit(CouponTemplateEntity template, Long userId) {
        Integer userReceiveLimit = template.getUserReceiveLimit();
        if (userReceiveLimit == null || userReceiveLimit <= 0) {
            return;
        }
        int receivedCount = couponRepository.countUserReceivedCoupons(template.getId(), userId);
        if (receivedCount >= userReceiveLimit) {
            throw new IllegalArgumentException("coupon receive limit reached");
        }
    }

    private void validateCouponCanUse(UserCouponEntity coupon, Long orderAmount, PackageTradeSnapshot packageSnapshot, LocalDateTime now) {
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
        validateCouponScope(coupon, packageSnapshot);
    }

    private String resolveReleaseStatus(UserCouponEntity coupon, LocalDateTime now) {
        if (coupon != null && coupon.getValidEndTime() != null && coupon.getValidEndTime().isBefore(now)) {
            return CouponStatusConstants.EXPIRED;
        }
        return CouponStatusConstants.UNUSED;
    }

    private void validateCouponScope(UserCouponEntity coupon, PackageTradeSnapshot packageSnapshot) {
        if (packageSnapshot == null) {
            throw new IllegalArgumentException("package snapshot required");
        }
        String scopeType = normalizeScopeType(coupon.getScopeType());
        if (CouponScopeConstants.ALL.equals(scopeType)) {
            return;
        }
        if (CouponScopeConstants.SHOP.equals(scopeType)) {
            if (coupon.getScopeShopId() == null || !coupon.getScopeShopId().equals(packageSnapshot.getShopId())) {
                throw new IllegalArgumentException("coupon scope not matched");
            }
            return;
        }
        if (CouponScopeConstants.PACKAGE.equals(scopeType)) {
            if (coupon.getScopePackageId() == null || !coupon.getScopePackageId().equals(packageSnapshot.getPackageId())) {
                throw new IllegalArgumentException("coupon scope not matched");
            }
            return;
        }
        throw new IllegalArgumentException("coupon scope invalid");
    }

    private void validateScopeConfig(String rawScopeType, Long scopeShopId, Long scopePackageId) {
        String scopeType = normalizeScopeType(rawScopeType);
        if (CouponScopeConstants.ALL.equals(scopeType)) {
            return;
        }
        if (CouponScopeConstants.SHOP.equals(scopeType)) {
            if (scopeShopId == null) {
                throw new IllegalArgumentException("coupon scope shop required");
            }
            return;
        }
        if (CouponScopeConstants.PACKAGE.equals(scopeType)) {
            if (scopePackageId == null) {
                throw new IllegalArgumentException("coupon scope package required");
            }
            return;
        }
        throw new IllegalArgumentException("coupon scope invalid");
    }

    private String normalizeScopeType(String scopeType) {
        if (scopeType == null || scopeType.trim().isEmpty()) {
            return CouponScopeConstants.ALL;
        }
        return scopeType.trim().toUpperCase();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeExpireLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_EXPIRE_LIMIT;
        }
        return Math.min(limit, MAX_EXPIRE_LIMIT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
