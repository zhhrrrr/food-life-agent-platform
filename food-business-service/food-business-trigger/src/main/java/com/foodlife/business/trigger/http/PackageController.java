package com.foodlife.business.trigger.http;

import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeRecordEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeResult;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;
import com.foodlife.business.domain.packagee.service.PackageDomainService;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/package")
public class PackageController {

    private final PackageDomainService packageDomainService;

    public PackageController(PackageDomainService packageDomainService) {
        this.packageDomainService = packageDomainService;
    }

    @GetMapping("/{id}")
    public Response<MealPackageEntity> queryPackageById(@PathVariable Long id) {
        return Response.success(packageDomainService.queryPackageById(id));
    }

    @GetMapping("/of/shop")
    public Response<List<MealPackageEntity>> queryPackagesByShopId(@RequestParam Long shopId) {
        return Response.success(packageDomainService.queryPackagesByShopId(shopId));
    }

    @GetMapping("/trade-snapshot/{packageId}")
    public Response<PackageTradeSnapshotEntity> queryTradeSnapshot(@PathVariable Long packageId) {
        PackageTradeSnapshotEntity snapshot = packageDomainService.queryTradeSnapshot(packageId);
        if (snapshot == null) {
            return Response.fail("404", "package not found");
        }
        return Response.success(snapshot);
    }

    @GetMapping("/stock-change-records")
    public Response<List<PackageStockChangeRecordEntity>> listStockChangeRecords(@RequestParam(required = false) String operationIdPrefix,
                                                                                 @RequestParam(required = false) Long packageId,
                                                                                 @RequestParam(required = false) Integer limit) {
        return Response.success(packageDomainService.listStockChangeRecords(operationIdPrefix, packageId, limit));
    }

    @PostMapping("/{packageId}/stock/occupy")
    public Response<PackageStockChangeResult> occupyPackageStock(@PathVariable Long packageId,
                                                                 @RequestParam Integer quantity,
                                                                 @RequestParam(required = false) String operationId) {
        try {
            return Response.success(packageDomainService.occupyPackageStock(packageId, quantity, operationId));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/{packageId}/stock/release")
    public Response<PackageStockChangeResult> releasePackageStock(@PathVariable Long packageId,
                                                                 @RequestParam Integer quantity,
                                                                 @RequestParam(required = false) String operationId) {
        try {
            return Response.success(packageDomainService.releasePackageStock(packageId, quantity, operationId));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/{packageId}/sold/confirm")
    public Response<PackageStockChangeResult> confirmPackageSold(@PathVariable Long packageId,
                                                                @RequestParam Integer quantity,
                                                                @RequestParam(required = false) String operationId) {
        try {
            return Response.success(packageDomainService.confirmPackageSold(packageId, quantity, operationId));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/{packageId}/sold/rollback")
    public Response<PackageStockChangeResult> rollbackPackageSold(@PathVariable Long packageId,
                                                                 @RequestParam Integer quantity,
                                                                 @RequestParam(required = false) String operationId) {
        try {
            return Response.success(packageDomainService.rollbackPackageSold(packageId, quantity, operationId));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }
}
