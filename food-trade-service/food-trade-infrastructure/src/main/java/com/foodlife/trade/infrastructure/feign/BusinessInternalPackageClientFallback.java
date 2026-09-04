package com.foodlife.trade.infrastructure.feign;

import com.foodlife.business.api.dto.AdjustPackageStockRequestDTO;
import com.foodlife.business.api.dto.AdjustPackageStockResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeRecordResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeResponseDTO;
import com.foodlife.business.types.response.Response;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessInternalPackageClientFallback implements BusinessInternalPackageClient {

    @Override
    public Response<AdjustPackageStockResponseDTO> adjustPackageStock(Long packageId, AdjustPackageStockRequestDTO request) {
        return Response.fail("503", "package stock adjust service unavailable");
    }

    @Override
    public Response<List<PackageStockChangeRecordResponseDTO>> listStockChangeRecords(String operationIdPrefix, Long packageId, Integer limit) {
        return Response.fail("503", "package stock record service unavailable");
    }

    @Override
    public Response<PackageStockChangeResponseDTO> occupyPackageStock(Long packageId, Integer quantity, String operationId) {
        return Response.fail("503", "package stock service unavailable");
    }

    @Override
    public Response<PackageStockChangeResponseDTO> releasePackageStock(Long packageId, Integer quantity, String operationId) {
        return Response.fail("503", "package stock release service unavailable");
    }

    @Override
    public Response<PackageStockChangeResponseDTO> confirmPackageSold(Long packageId, Integer quantity, String operationId) {
        return Response.fail("503", "package sold confirm service unavailable");
    }

    @Override
    public Response<PackageStockChangeResponseDTO> rollbackPackageSold(Long packageId, Integer quantity, String operationId) {
        return Response.fail("503", "package sold rollback service unavailable");
    }
}
