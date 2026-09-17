package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodlife.trade.domain.order.search.model.SearchDocumentEntity;
import com.foodlife.trade.domain.order.search.repository.ISearchDocumentRepository;
import com.foodlife.trade.infrastructure.dao.ICouponTemplateMapper;
import com.foodlife.trade.infrastructure.dao.IGroupBuyActivityMapper;
import com.foodlife.trade.infrastructure.dao.ISeckillActivityMapper;
import com.foodlife.trade.infrastructure.dao.po.CouponTemplatePO;
import com.foodlife.trade.infrastructure.dao.po.GroupBuyActivityPO;
import com.foodlife.trade.infrastructure.dao.po.SeckillActivityPO;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class SearchDocumentRepository implements ISearchDocumentRepository {

    private static final String ACTIVITY_SOURCE = "food_activity_index";
    private static final String COUPON_SOURCE = "food_coupon_index";

    private final IGroupBuyActivityMapper groupBuyActivityMapper;
    private final ISeckillActivityMapper seckillActivityMapper;
    private final ICouponTemplateMapper couponTemplateMapper;

    public SearchDocumentRepository(IGroupBuyActivityMapper groupBuyActivityMapper,
                                    ISeckillActivityMapper seckillActivityMapper,
                                    ICouponTemplateMapper couponTemplateMapper) {
        this.groupBuyActivityMapper = groupBuyActivityMapper;
        this.seckillActivityMapper = seckillActivityMapper;
        this.couponTemplateMapper = couponTemplateMapper;
    }

    @Override
    public List<SearchDocumentEntity> listActivityDocuments(Integer limit) {
        List<SearchDocumentEntity> groupBuyDocuments = groupBuyActivityMapper.selectList(new LambdaQueryWrapper<GroupBuyActivityPO>()
                        .eq(GroupBuyActivityPO::getActivityStatus, 1)
                        .orderByDesc(GroupBuyActivityPO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toGroupBuyDocument)
                .collect(Collectors.toList());

        List<SearchDocumentEntity> seckillDocuments = seckillActivityMapper.selectList(new LambdaQueryWrapper<SeckillActivityPO>()
                        .eq(SeckillActivityPO::getActivityStatus, 1)
                        .orderByDesc(SeckillActivityPO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toSeckillDocument)
                .collect(Collectors.toList());

        return Stream.concat(groupBuyDocuments.stream(), seckillDocuments.stream())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchDocumentEntity> listCouponDocuments(Integer limit) {
        return couponTemplateMapper.selectList(new LambdaQueryWrapper<CouponTemplatePO>()
                        .eq(CouponTemplatePO::getTemplateStatus, 1)
                        .orderByDesc(CouponTemplatePO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toCouponDocument)
                .collect(Collectors.toList());
    }

    private SearchDocumentEntity toGroupBuyDocument(GroupBuyActivityPO activity) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("activityId", activity.getId());
        metadata.put("activityType", "GROUP_BUY");
        metadata.put("packageId", activity.getPackageId());
        metadata.put("targetCount", activity.getTargetCount());
        metadata.put("userTakeLimit", activity.getUserTakeLimit());
        metadata.put("activityPrice", activity.getGroupPrice());
        metadata.put("stock", activity.getStock());
        metadata.put("validStartTime", activity.getValidStartTime());
        metadata.put("validEndTime", activity.getValidEndTime());

        SearchDocumentEntity document = new SearchDocumentEntity();
        document.setId("group-buy-activity-" + activity.getId());
        document.setTitle(activity.getActivityName());
        document.setContent(joinText(
                activity.getActivityName(),
                "group buy",
                "target count " + activity.getTargetCount(),
                "user limit " + activity.getUserTakeLimit()
        ));
        document.setSource(ACTIVITY_SOURCE);
        document.setCategory("GROUP_BUY");
        document.setMetadata(metadata);
        return document;
    }

    private SearchDocumentEntity toSeckillDocument(SeckillActivityPO activity) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("activityId", activity.getId());
        metadata.put("activityType", "SECKILL");
        metadata.put("packageId", activity.getPackageId());
        metadata.put("userTakeLimit", activity.getUserTakeLimit());
        metadata.put("activityPrice", activity.getSeckillPrice());
        metadata.put("stock", activity.getStock());
        metadata.put("validStartTime", activity.getValidStartTime());
        metadata.put("validEndTime", activity.getValidEndTime());

        SearchDocumentEntity document = new SearchDocumentEntity();
        document.setId("seckill-activity-" + activity.getId());
        document.setTitle(activity.getActivityName());
        document.setContent(joinText(
                activity.getActivityName(),
                "seckill",
                "limited time",
                "user limit " + activity.getUserTakeLimit()
        ));
        document.setSource(ACTIVITY_SOURCE);
        document.setCategory("SECKILL");
        document.setMetadata(metadata);
        return document;
    }

    private SearchDocumentEntity toCouponDocument(CouponTemplatePO coupon) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("couponId", coupon.getId());
        metadata.put("couponName", coupon.getCouponName());
        metadata.put("couponType", coupon.getCouponType());
        metadata.put("thresholdAmount", coupon.getThresholdAmount());
        metadata.put("discountAmount", coupon.getDiscountAmount());
        metadata.put("scopeType", coupon.getScopeType());
        metadata.put("scopeShopId", coupon.getScopeShopId());
        metadata.put("scopePackageId", coupon.getScopePackageId());
        metadata.put("userReceiveLimit", coupon.getUserReceiveLimit());
        metadata.put("totalStock", coupon.getTotalStock());
        metadata.put("receivedCount", coupon.getReceivedCount());
        metadata.put("validStartTime", coupon.getValidStartTime());
        metadata.put("validEndTime", coupon.getValidEndTime());

        SearchDocumentEntity document = new SearchDocumentEntity();
        document.setId("coupon-template-" + coupon.getId());
        document.setTitle(coupon.getCouponName());
        document.setContent(joinText(
                coupon.getCouponName(),
                coupon.getCouponType(),
                coupon.getScopeType(),
                "threshold " + coupon.getThresholdAmount(),
                "discount " + coupon.getDiscountAmount()
        ));
        document.setSource(COUPON_SOURCE);
        document.setCategory("coupon_template");
        document.setMetadata(metadata);
        return document;
    }

    private String joinText(String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }
}
