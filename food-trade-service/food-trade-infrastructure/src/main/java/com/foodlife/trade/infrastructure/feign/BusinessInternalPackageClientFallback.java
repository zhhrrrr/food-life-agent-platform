package com.foodlife.trade.infrastructure.feign;

import com.foodlife.business.api.dto.AdjustPackageStockRequestDTO;
import com.foodlife.business.api.dto.AdjustPackageStockResponseDTO;
import com.foodlife.business.types.response.Response;
import org.springframework.stereotype.Component;

@Component
public class BusinessInternalPackageClientFallback implements BusinessInternalPackageClient {

    @Override
    public Response<AdjustPackageStockResponseDTO> adjustPackageStock(Long packageId, AdjustPackageStockRequestDTO request) {
        return Response.fail("503", "package stock adjust service unavailable");
    }
}
