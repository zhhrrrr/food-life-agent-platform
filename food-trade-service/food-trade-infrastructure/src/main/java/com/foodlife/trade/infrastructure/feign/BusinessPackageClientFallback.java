package com.foodlife.trade.infrastructure.feign;

import com.foodlife.business.api.dto.PackageTradeSnapshotResponseDTO;
import com.foodlife.business.types.response.Response;
import org.springframework.stereotype.Component;

@Component
public class BusinessPackageClientFallback extends AbstractBusinessPackageStockFallback implements BusinessPackageClient {

    @Override
    public Response<PackageTradeSnapshotResponseDTO> queryTradeSnapshot(Long packageId) {
        return Response.fail("503", "服务繁忙，请稍后再试");
    }
}
