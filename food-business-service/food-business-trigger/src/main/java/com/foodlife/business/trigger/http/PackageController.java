package com.foodlife.business.trigger.http;

import com.foodlife.business.api.dto.PackageTradeSnapshotResponseDTO;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;
import com.foodlife.business.domain.packagee.service.PackageDomainService;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
