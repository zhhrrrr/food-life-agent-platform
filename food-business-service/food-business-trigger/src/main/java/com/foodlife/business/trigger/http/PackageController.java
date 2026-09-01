package com.foodlife.business.trigger.http;

import com.foodlife.business.api.dto.PackageStockChangeRecordResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeResponseDTO;
import com.foodlife.business.api.dto.PackageTradeSnapshotResponseDTO;
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
    public Response<PackageTradeSnapshotResponseDTO> queryTradeSnapshot(@PathVariable Long packageId) {
        PackageTradeSnapshotEntity snapshot = packageDomainService.queryTradeSnapshot(packageId);
        if (snapshot == null) {
            return Response.fail("404", "package not found");
        }
        return Response.success(toTradeSnapshotResponse(snapshot));
    }

    @GetMapping("/stock-change-records")
    public Response<List<PackageStockChangeRecordResponseDTO>> listStockChangeRecords(@RequestParam(required = false) String operationIdPrefix,
                                                                                      @RequestParam(required = false) Long packageId,
                                                                                      @RequestParam(required = false) Integer limit) {
        return Response.success(packageDomainService.listStockChangeRecords(operationIdPrefix, packageId, limit)
                .stream()
                .map(this::toStockChangeRecordResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PostMapping("/{packageId}/stock/occupy")
    public Response<PackageStockChangeResponseDTO> occupyPackageStock(@PathVariable Long packageId,
                                                                      @RequestParam Integer quantity,
                                                                      @RequestParam(required = false) String operationId) {
        try {
            return Response.success(toStockChangeResponse(packageDomainService.occupyPackageStock(packageId, quantity, operationId)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/{packageId}/stock/release")
    public Response<PackageStockChangeResponseDTO> releasePackageStock(@PathVariable Long packageId,
                                                                       @RequestParam Integer quantity,
                                                                       @RequestParam(required = false) String operationId) {
        try {
            return Response.success(toStockChangeResponse(packageDomainService.releasePackageStock(packageId, quantity, operationId)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/{packageId}/sold/confirm")
    public Response<PackageStockChangeResponseDTO> confirmPackageSold(@PathVariable Long packageId,
                                                                     @RequestParam Integer quantity,
                                                                     @RequestParam(required = false) String operationId) {
        try {
            return Response.success(toStockChangeResponse(packageDomainService.confirmPackageSold(packageId, quantity, operationId)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/{packageId}/sold/rollback")
    public Response<PackageStockChangeResponseDTO> rollbackPackageSold(@PathVariable Long packageId,
                                                                      @RequestParam Integer quantity,
                                                                      @RequestParam(required = false) String operationId) {
        try {
            return Response.success(toStockChangeResponse(packageDomainService.rollbackPackageSold(packageId, quantity, operationId)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private PackageTradeSnapshotResponseDTO toTradeSnapshotResponse(PackageTradeSnapshotEntity source) {
        PackageTradeSnapshotResponseDTO target = new PackageTradeSnapshotResponseDTO();
        target.setShopId(source.getShopId());
        target.setShopName(source.getShopName());
        target.setPackageId(source.getPackageId());
        target.setPackageName(source.getPackageName());
        target.setPackageDescription(source.getPackageDescription());
        target.setCoverImage(source.getCoverImage());
        target.setPrice(source.getPrice());
        target.setOriginalPrice(source.getOriginalPrice());
        target.setStock(source.getStock());
        target.setPackageStatus(source.getPackageStatus());
        target.setUseRule(source.getUseRule());
        return target;
    }

    private PackageStockChangeRecordResponseDTO toStockChangeRecordResponse(PackageStockChangeRecordEntity source) {
        PackageStockChangeRecordResponseDTO target = new PackageStockChangeRecordResponseDTO();
        target.setId(source.getId());
        target.setOperationId(source.getOperationId());
        target.setPackageId(source.getPackageId());
        target.setQuantity(source.getQuantity());
        target.setChangeType(source.getChangeType());
        target.setChangeStatus(source.getChangeStatus());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private PackageStockChangeResponseDTO toStockChangeResponse(PackageStockChangeResult source) {
        PackageStockChangeResponseDTO target = new PackageStockChangeResponseDTO();
        target.setPackageId(source.getPackageId());
        target.setQuantity(source.getQuantity());
        target.setStock(source.getStock());
        target.setSold(source.getSold());
        target.setChangeType(source.getChangeType());
        return target;
    }
}
