package com.foodlife.business.trigger.http;

import com.foodlife.business.domain.packagee.model.MealPackageEntity;
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
}
