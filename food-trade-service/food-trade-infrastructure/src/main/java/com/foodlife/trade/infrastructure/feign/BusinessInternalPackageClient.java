package com.foodlife.trade.infrastructure.feign;

import com.foodlife.business.api.dto.AdjustPackageStockRequestDTO;
import com.foodlife.business.api.dto.AdjustPackageStockResponseDTO;
import com.foodlife.business.types.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "businessInternalPackageClient",
        name = "food-business-service",
        path = "/api/internal/package",
        fallback = BusinessInternalPackageClientFallback.class)
public interface BusinessInternalPackageClient {

    @PostMapping("/{packageId}/stock/adjust")
    Response<AdjustPackageStockResponseDTO> adjustPackageStock(@PathVariable("packageId") Long packageId,
                                                               @RequestBody AdjustPackageStockRequestDTO request);
}
