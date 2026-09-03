package com.foodlife.business.trigger.http;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.foodlife.business.api.dto.AdjustPackageStockRequestDTO;
import com.foodlife.business.api.dto.AdjustPackageStockResponseDTO;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockCommand;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockResult;
import com.foodlife.business.domain.packagee.service.PackageDomainService;
import com.foodlife.business.trigger.sentinel.BusinessSentinelResources;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/package")
public class PackageInternalController {

    private final PackageDomainService packageDomainService;

    public PackageInternalController(PackageDomainService packageDomainService) {
        this.packageDomainService = packageDomainService;
    }

    @PostMapping("/{packageId}/stock/adjust")
    @SentinelResource(value = BusinessSentinelResources.PACKAGE_STOCK_ADMIN_ADJUST, blockHandler = "adjustPackageStockBlock")
    public Response<AdjustPackageStockResponseDTO> adjustPackageStock(@PathVariable Long packageId,
                                                                      @RequestBody AdjustPackageStockRequestDTO request) {
        try {
            return Response.success(toResponse(packageDomainService.adjustPackageStock(toCommand(packageId, request))));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    public Response<AdjustPackageStockResponseDTO> adjustPackageStockBlock(Long packageId,
                                                                          AdjustPackageStockRequestDTO request,
                                                                          BlockException e) {
        return Response.fail("429", "package stock adjust busy, please try again later");
    }

    private AdjustPackageStockCommand toCommand(Long packageId, AdjustPackageStockRequestDTO request) {
        AdjustPackageStockCommand command = new AdjustPackageStockCommand();
        command.setPackageId(packageId);
        if (request != null) {
            command.setOperatorId(request.getOperatorId());
            command.setAdjustQuantity(request.getAdjustQuantity());
            command.setReason(request.getReason());
            command.setOperationId(request.getOperationId());
        }
        return command;
    }

    private AdjustPackageStockResponseDTO toResponse(AdjustPackageStockResult source) {
        AdjustPackageStockResponseDTO response = new AdjustPackageStockResponseDTO();
        response.setPackageId(source.getPackageId());
        response.setOperatorId(source.getOperatorId());
        response.setAdjustQuantity(source.getAdjustQuantity());
        response.setStock(source.getStock());
        response.setSold(source.getSold());
        response.setChangeType(source.getChangeType());
        response.setOperationId(source.getOperationId());
        return response;
    }
}
