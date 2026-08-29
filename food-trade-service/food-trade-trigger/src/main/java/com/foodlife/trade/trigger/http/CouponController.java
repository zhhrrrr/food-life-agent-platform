package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.CouponExpireScanResponseDTO;
import com.foodlife.trade.api.dto.CouponTemplateListResponseDTO;
import com.foodlife.trade.api.dto.ReceiveCouponResponseDTO;
import com.foodlife.trade.api.dto.UserCouponListResponseDTO;
import com.foodlife.trade.domain.order.coupon.model.CouponExpireScanResult;
import com.foodlife.trade.domain.order.coupon.model.CouponTemplateEntity;
import com.foodlife.trade.domain.order.coupon.model.UserCouponEntity;
import com.foodlife.trade.domain.order.coupon.service.CouponService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/templates")
    public Response<CouponTemplateListResponseDTO> listAvailableTemplates(@RequestParam(required = false) Integer limit) {
        CouponTemplateListResponseDTO response = new CouponTemplateListResponseDTO();
        response.setTemplates(couponService.listAvailableTemplates(limit).stream()
                .map(this::toTemplateInfo)
                .collect(Collectors.toList()));
        return Response.success(response);
    }

    @PostMapping("/templates/{templateId}/receive")
    public Response<ReceiveCouponResponseDTO> receiveCoupon(@PathVariable Long templateId) {
        try {
            return Response.success(toReceiveResponse(couponService.receiveCoupon(templateId, UserHolder.getUserId())));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/mine")
    public Response<UserCouponListResponseDTO> listMyCoupons(@RequestParam(required = false) String couponStatus,
                                                             @RequestParam(required = false) Integer limit) {
        try {
            UserCouponListResponseDTO response = new UserCouponListResponseDTO();
            response.setCoupons(couponService.listUserCoupons(UserHolder.getUserId(), couponStatus, limit).stream()
                    .map(this::toUserCouponInfo)
                    .collect(Collectors.toList()));
            return Response.success(response);
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/expired/scan")
    public Response<CouponExpireScanResponseDTO> scanExpiredCoupons(@RequestParam(required = false) Integer limit) {
        return Response.success(toExpireScanResponse(couponService.expireUnusedCoupons(limit)));
    }

    private CouponTemplateListResponseDTO.CouponTemplateInfo toTemplateInfo(CouponTemplateEntity source) {
        CouponTemplateListResponseDTO.CouponTemplateInfo info = new CouponTemplateListResponseDTO.CouponTemplateInfo();
        info.setTemplateId(source.getId());
        info.setCouponName(source.getCouponName());
        info.setCouponType(source.getCouponType());
        info.setThresholdAmount(source.getThresholdAmount());
        info.setDiscountAmount(source.getDiscountAmount());
        info.setScopeType(source.getScopeType());
        info.setScopeShopId(source.getScopeShopId());
        info.setScopePackageId(source.getScopePackageId());
        info.setUserReceiveLimit(source.getUserReceiveLimit());
        info.setValidStartTime(source.getValidStartTime());
        info.setValidEndTime(source.getValidEndTime());
        info.setTotalStock(source.getTotalStock());
        info.setReceivedCount(source.getReceivedCount());
        info.setTemplateStatus(source.getTemplateStatus());
        return info;
    }

    private ReceiveCouponResponseDTO toReceiveResponse(UserCouponEntity source) {
        ReceiveCouponResponseDTO response = new ReceiveCouponResponseDTO();
        response.setUserCouponId(source.getId());
        response.setTemplateId(source.getTemplateId());
        response.setUserId(source.getUserId());
        response.setCouponName(source.getCouponName());
        response.setThresholdAmount(source.getThresholdAmount());
        response.setDiscountAmount(source.getDiscountAmount());
        response.setScopeType(source.getScopeType());
        response.setScopeShopId(source.getScopeShopId());
        response.setScopePackageId(source.getScopePackageId());
        response.setCouponStatus(source.getCouponStatus());
        response.setValidStartTime(source.getValidStartTime());
        response.setValidEndTime(source.getValidEndTime());
        return response;
    }

    private UserCouponListResponseDTO.UserCouponInfo toUserCouponInfo(UserCouponEntity source) {
        UserCouponListResponseDTO.UserCouponInfo info = new UserCouponListResponseDTO.UserCouponInfo();
        info.setUserCouponId(source.getId());
        info.setTemplateId(source.getTemplateId());
        info.setUserId(source.getUserId());
        info.setCouponName(source.getCouponName());
        info.setCouponType(source.getCouponType());
        info.setThresholdAmount(source.getThresholdAmount());
        info.setDiscountAmount(source.getDiscountAmount());
        info.setScopeType(source.getScopeType());
        info.setScopeShopId(source.getScopeShopId());
        info.setScopePackageId(source.getScopePackageId());
        info.setCouponStatus(source.getCouponStatus());
        info.setUsedOrderId(source.getUsedOrderId());
        info.setValidStartTime(source.getValidStartTime());
        info.setValidEndTime(source.getValidEndTime());
        info.setReceiveTime(source.getReceiveTime());
        info.setUseTime(source.getUseTime());
        return info;
    }

    private CouponExpireScanResponseDTO toExpireScanResponse(CouponExpireScanResult source) {
        CouponExpireScanResponseDTO response = new CouponExpireScanResponseDTO();
        response.setScanTime(source.getScanTime());
        response.setExpireBefore(source.getExpireBefore());
        response.setExpiredCount(source.getExpiredCount());
        response.setLimit(source.getLimit());
        return response;
    }
}
