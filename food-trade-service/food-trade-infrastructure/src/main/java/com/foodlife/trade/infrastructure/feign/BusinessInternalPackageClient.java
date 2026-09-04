package com.foodlife.trade.infrastructure.feign;

import com.foodlife.business.api.dto.AdjustPackageStockRequestDTO;
import com.foodlife.business.api.dto.AdjustPackageStockResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeRecordResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeResponseDTO;
import com.foodlife.business.types.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(contextId = "businessInternalPackageClient",
        name = "food-business-service",
        path = "/api/internal/package",
        fallback = BusinessInternalPackageClientFallback.class)
public interface BusinessInternalPackageClient {

    @PostMapping("/{packageId}/stock/adjust")
    Response<AdjustPackageStockResponseDTO> adjustPackageStock(@PathVariable("packageId") Long packageId,
                                                               @RequestBody AdjustPackageStockRequestDTO request);

    @GetMapping("/stock-change-records")
    Response<List<PackageStockChangeRecordResponseDTO>> listStockChangeRecords(@RequestParam(value = "operationIdPrefix", required = false) String operationIdPrefix,
                                                                               @RequestParam(value = "packageId", required = false) Long packageId,
                                                                               @RequestParam(value = "limit", required = false) Integer limit);

    @PostMapping("/{packageId}/stock/occupy")
    Response<PackageStockChangeResponseDTO> occupyPackageStock(@PathVariable("packageId") Long packageId,
                                                               @RequestParam("quantity") Integer quantity,
                                                               @RequestParam(value = "operationId", required = false) String operationId);

    @PostMapping("/{packageId}/stock/release")
    Response<PackageStockChangeResponseDTO> releasePackageStock(@PathVariable("packageId") Long packageId,
                                                                @RequestParam("quantity") Integer quantity,
                                                                @RequestParam(value = "operationId", required = false) String operationId);

    @PostMapping("/{packageId}/sold/confirm")
    Response<PackageStockChangeResponseDTO> confirmPackageSold(@PathVariable("packageId") Long packageId,
                                                               @RequestParam("quantity") Integer quantity,
                                                               @RequestParam(value = "operationId", required = false) String operationId);

    @PostMapping("/{packageId}/sold/rollback")
    Response<PackageStockChangeResponseDTO> rollbackPackageSold(@PathVariable("packageId") Long packageId,
                                                                @RequestParam("quantity") Integer quantity,
                                                                @RequestParam(value = "operationId", required = false) String operationId);
}
