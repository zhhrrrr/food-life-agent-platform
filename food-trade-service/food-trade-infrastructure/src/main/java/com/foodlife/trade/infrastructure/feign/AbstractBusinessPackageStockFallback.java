package com.foodlife.trade.infrastructure.feign;

import com.foodlife.business.api.dto.PackageStockChangeRecordResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeResponseDTO;
import com.foodlife.business.types.response.Response;

import java.util.List;

abstract class AbstractBusinessPackageStockFallback {

    public Response<List<PackageStockChangeRecordResponseDTO>> listStockChangeRecords(String operationIdPrefix, Long packageId, Integer limit) {
        return Response.fail("503", "package stock record service unavailable");
    }

    public Response<PackageStockChangeResponseDTO> occupyPackageStock(Long packageId, Integer quantity, String operationId) {
        return Response.fail("503", "package stock service unavailable");
    }

    public Response<PackageStockChangeResponseDTO> releasePackageStock(Long packageId, Integer quantity, String operationId) {
        return Response.fail("503", "package stock release service unavailable");
    }

    public Response<PackageStockChangeResponseDTO> confirmPackageSold(Long packageId, Integer quantity, String operationId) {
        return Response.fail("503", "package sold confirm service unavailable");
    }

    public Response<PackageStockChangeResponseDTO> rollbackPackageSold(Long packageId, Integer quantity, String operationId) {
        return Response.fail("503", "package sold rollback service unavailable");
    }
}
