package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.coupon.constant.CouponStatusConstants;
import com.foodlife.trade.domain.order.coupon.constant.CouponTemplateStatusConstants;
import com.foodlife.trade.domain.order.coupon.model.CouponTemplateEntity;
import com.foodlife.trade.domain.order.coupon.model.UserCouponEntity;
import com.foodlife.trade.domain.order.coupon.repository.ICouponRepository;
import com.foodlife.trade.infrastructure.dao.ICouponTemplateMapper;
import com.foodlife.trade.infrastructure.dao.IUserCouponMapper;
import com.foodlife.trade.infrastructure.dao.po.CouponTemplatePO;
import com.foodlife.trade.infrastructure.dao.po.UserCouponPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class CouponRepository implements ICouponRepository {

    private final ICouponTemplateMapper couponTemplateMapper;
    private final IUserCouponMapper userCouponMapper;

    public CouponRepository(ICouponTemplateMapper couponTemplateMapper, IUserCouponMapper userCouponMapper) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
    }

    @Override
    public List<CouponTemplateEntity> listAvailableTemplates(LocalDateTime now, Integer limit) {
        return couponTemplateMapper.selectList(new LambdaQueryWrapper<CouponTemplatePO>()
                        .eq(CouponTemplatePO::getTemplateStatus, CouponTemplateStatusConstants.ENABLED)
                        .le(CouponTemplatePO::getValidStartTime, now)
                        .ge(CouponTemplatePO::getValidEndTime, now)
                        .apply("received_count < total_stock")
                        .orderByAsc(CouponTemplatePO::getThresholdAmount)
                        .last("limit " + limit))
                .stream()
                .map(this::toTemplateEntity)
                .collect(Collectors.toList());
    }

    @Override
    public CouponTemplateEntity findTemplateById(Long templateId) {
        return toTemplateEntity(couponTemplateMapper.selectById(templateId));
    }

    @Override
    public boolean increaseReceivedCount(Long templateId) {
        CouponTemplatePO updatePO = new CouponTemplatePO();
        updatePO.setUpdateTime(LocalDateTime.now());
        int updated = couponTemplateMapper.update(updatePO, new LambdaUpdateWrapper<CouponTemplatePO>()
                .setSql("received_count = received_count + 1")
                .eq(CouponTemplatePO::getId, templateId)
                .eq(CouponTemplatePO::getTemplateStatus, CouponTemplateStatusConstants.ENABLED)
                .apply("received_count < total_stock"));
        return updated > 0;
    }

    @Override
    public int countUserReceivedCoupons(Long templateId, Long userId) {
        Long count = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCouponPO>()
                .eq(UserCouponPO::getTemplateId, templateId)
                .eq(UserCouponPO::getUserId, userId));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public UserCouponEntity saveUserCoupon(UserCouponEntity userCoupon) {
        UserCouponPO po = toUserCouponPO(userCoupon);
        userCouponMapper.insert(po);
        userCoupon.setId(po.getId());
        return userCoupon;
    }

    @Override
    public UserCouponEntity findUserCouponByIdAndUserId(Long userCouponId, Long userId) {
        return toUserCouponEntity(userCouponMapper.selectOne(new LambdaQueryWrapper<UserCouponPO>()
                .eq(UserCouponPO::getId, userCouponId)
                .eq(UserCouponPO::getUserId, userId)
                .last("limit 1")));
    }

    @Override
    public List<UserCouponEntity> listUserCoupons(Long userId, String couponStatus, Integer limit) {
        LambdaQueryWrapper<UserCouponPO> wrapper = new LambdaQueryWrapper<UserCouponPO>()
                .eq(UserCouponPO::getUserId, userId)
                .orderByDesc(UserCouponPO::getId)
                .last("limit " + limit);
        if (couponStatus != null) {
            wrapper.eq(UserCouponPO::getCouponStatus, couponStatus);
        }
        return userCouponMapper.selectList(wrapper).stream().map(this::toUserCouponEntity).collect(Collectors.toList());
    }

    @Override
    public boolean markCouponUsed(Long userCouponId, Long userId, Long orderId, LocalDateTime useTime) {
        UserCouponPO updatePO = new UserCouponPO();
        updatePO.setCouponStatus(CouponStatusConstants.USED);
        updatePO.setUsedOrderId(orderId);
        updatePO.setUseTime(useTime);
        updatePO.setUpdateTime(LocalDateTime.now());
        int updated = userCouponMapper.update(updatePO, new LambdaUpdateWrapper<UserCouponPO>()
                .eq(UserCouponPO::getId, userCouponId)
                .eq(UserCouponPO::getUserId, userId)
                .eq(UserCouponPO::getCouponStatus, CouponStatusConstants.UNUSED));
        return updated > 0;
    }

    @Override
    public boolean releaseUsedCoupon(Long userCouponId, Long userId, Long orderId, String couponStatus) {
        int updated = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCouponPO>()
                .set(UserCouponPO::getCouponStatus, couponStatus)
                .set(UserCouponPO::getUsedOrderId, null)
                .set(UserCouponPO::getUseTime, null)
                .set(UserCouponPO::getUpdateTime, LocalDateTime.now())
                .eq(UserCouponPO::getId, userCouponId)
                .eq(UserCouponPO::getUserId, userId)
                .eq(UserCouponPO::getUsedOrderId, orderId)
                .eq(UserCouponPO::getCouponStatus, CouponStatusConstants.USED));
        return updated > 0;
    }

    @Override
    public int expireUnusedCoupons(LocalDateTime now, Integer limit) {
        return userCouponMapper.update(null, new LambdaUpdateWrapper<UserCouponPO>()
                .set(UserCouponPO::getCouponStatus, CouponStatusConstants.EXPIRED)
                .set(UserCouponPO::getUpdateTime, now)
                .eq(UserCouponPO::getCouponStatus, CouponStatusConstants.UNUSED)
                .lt(UserCouponPO::getValidEndTime, now)
                .last("limit " + limit));
    }

    @Override
    public int expireUserUnusedCoupons(Long userId, LocalDateTime now, Integer limit) {
        return userCouponMapper.update(null, new LambdaUpdateWrapper<UserCouponPO>()
                .set(UserCouponPO::getCouponStatus, CouponStatusConstants.EXPIRED)
                .set(UserCouponPO::getUpdateTime, now)
                .eq(UserCouponPO::getUserId, userId)
                .eq(UserCouponPO::getCouponStatus, CouponStatusConstants.UNUSED)
                .lt(UserCouponPO::getValidEndTime, now)
                .last("limit " + limit));
    }

    private CouponTemplateEntity toTemplateEntity(CouponTemplatePO po) {
        if (po == null) {
            return null;
        }
        CouponTemplateEntity entity = new CouponTemplateEntity();
        entity.setId(po.getId());
        entity.setCouponName(po.getCouponName());
        entity.setCouponType(po.getCouponType());
        entity.setThresholdAmount(po.getThresholdAmount());
        entity.setDiscountAmount(po.getDiscountAmount());
        entity.setScopeType(po.getScopeType());
        entity.setScopeShopId(po.getScopeShopId());
        entity.setScopePackageId(po.getScopePackageId());
        entity.setUserReceiveLimit(po.getUserReceiveLimit());
        entity.setValidStartTime(po.getValidStartTime());
        entity.setValidEndTime(po.getValidEndTime());
        entity.setTotalStock(po.getTotalStock());
        entity.setReceivedCount(po.getReceivedCount());
        entity.setTemplateStatus(po.getTemplateStatus());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private UserCouponPO toUserCouponPO(UserCouponEntity entity) {
        UserCouponPO po = new UserCouponPO();
        po.setId(entity.getId());
        po.setTemplateId(entity.getTemplateId());
        po.setUserId(entity.getUserId());
        po.setCouponName(entity.getCouponName());
        po.setCouponType(entity.getCouponType());
        po.setThresholdAmount(entity.getThresholdAmount());
        po.setDiscountAmount(entity.getDiscountAmount());
        po.setScopeType(entity.getScopeType());
        po.setScopeShopId(entity.getScopeShopId());
        po.setScopePackageId(entity.getScopePackageId());
        po.setCouponStatus(entity.getCouponStatus());
        po.setUsedOrderId(entity.getUsedOrderId());
        po.setValidStartTime(entity.getValidStartTime());
        po.setValidEndTime(entity.getValidEndTime());
        po.setReceiveTime(entity.getReceiveTime());
        po.setUseTime(entity.getUseTime());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private UserCouponEntity toUserCouponEntity(UserCouponPO po) {
        if (po == null) {
            return null;
        }
        UserCouponEntity entity = new UserCouponEntity();
        entity.setId(po.getId());
        entity.setTemplateId(po.getTemplateId());
        entity.setUserId(po.getUserId());
        entity.setCouponName(po.getCouponName());
        entity.setCouponType(po.getCouponType());
        entity.setThresholdAmount(po.getThresholdAmount());
        entity.setDiscountAmount(po.getDiscountAmount());
        entity.setScopeType(po.getScopeType());
        entity.setScopeShopId(po.getScopeShopId());
        entity.setScopePackageId(po.getScopePackageId());
        entity.setCouponStatus(po.getCouponStatus());
        entity.setUsedOrderId(po.getUsedOrderId());
        entity.setValidStartTime(po.getValidStartTime());
        entity.setValidEndTime(po.getValidEndTime());
        entity.setReceiveTime(po.getReceiveTime());
        entity.setUseTime(po.getUseTime());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }
}
