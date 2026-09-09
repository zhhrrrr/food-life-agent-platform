package com.foodlife.business.trigger.http;

import com.foodlife.business.api.dto.CachePreheatResponseDTO;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.service.PackageDomainService;
import com.foodlife.business.domain.shop.model.ShopEntity;
import com.foodlife.business.domain.shop.service.ShopDomainService;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/operations/cache")
public class BusinessCacheOperationController {

    private final ShopDomainService shopDomainService;
    private final PackageDomainService packageDomainService;

    public BusinessCacheOperationController(ShopDomainService shopDomainService,
                                            PackageDomainService packageDomainService) {
        this.shopDomainService = shopDomainService;
        this.packageDomainService = packageDomainService;
    }

    @PostMapping("/preheat")
    public Response<CachePreheatResponseDTO> preheat(@RequestParam(required = false) Long shopId,
                                                     @RequestParam(required = false) Long packageId) {
        if (shopId == null && packageId == null) {
            return Response.fail("400", "shopId or packageId required");
        }
        CachePreheatResponseDTO response = new CachePreheatResponseDTO();
        response.setShopId(shopId);
        response.setPackageId(packageId);
        if (shopId != null) {
            ShopEntity shop = shopDomainService.queryShopById(shopId);
            List<MealPackageEntity> packages = packageDomainService.queryPackagesByShopId(shopId);
            response.setShopPreheated(shop != null);
            response.setPackageCount(packages == null ? 0 : packages.size());
        }
        if (packageId != null) {
            response.setPackageSnapshotPreheated(packageDomainService.queryTradeSnapshot(packageId) != null);
        }
        return Response.success(response);
    }
}
