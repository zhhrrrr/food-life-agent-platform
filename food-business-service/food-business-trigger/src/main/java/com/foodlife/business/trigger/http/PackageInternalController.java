package com.foodlife.business.trigger.http;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.foodlife.business.api.dto.AdjustPackageStockRequestDTO;
import com.foodlife.business.api.dto.AdjustPackageStockResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeRecordResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeResponseDTO;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockCommand;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockResult;
import com.foodlife.business.domain.packagee.model.PackageStockChangeRecordEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeResult;
import com.foodlife.business.domain.packagee.service.PackageDomainService;
import com.foodlife.business.trigger.sentinel.BusinessSentinelResources;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    @SentinelResource(value = BusinessSentinelResources.PACKAGE_STOCK_OCCUPY, blockHandler = "occupyPackageStockBlock")
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
    @SentinelResource(value = BusinessSentinelResources.PACKAGE_STOCK_RELEASE, blockHandler = "releasePackageStockBlock")
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
    @SentinelResource(value = BusinessSentinelResources.PACKAGE_SOLD_CONFIRM, blockHandler = "confirmPackageSoldBlock")
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
    @SentinelResource(value = BusinessSentinelResources.PACKAGE_SOLD_ROLLBACK, blockHandler = "rollbackPackageSoldBlock")
    public Response<PackageStockChangeResponseDTO> rollbackPackageSold(@PathVariable Long packageId,
                                                                       @RequestParam Integer quantity,
                                                                       @RequestParam(required = false) String operationId) {
        try {
            return Response.success(toStockChangeResponse(packageDomainService.rollbackPackageSold(packageId, quantity, operationId)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    public Response<PackageStockChangeResponseDTO> occupyPackageStockBlock(Long packageId, Integer quantity, String operationId, BlockException e) {
        return Response.fail("429", "package stock service busy, please try again later");
    }

    public Response<PackageStockChangeResponseDTO> releasePackageStockBlock(Long packageId, Integer quantity, String operationId, BlockException e) {
        return Response.fail("429", "package stock release busy, please try again later");
    }

    public Response<PackageStockChangeResponseDTO> confirmPackageSoldBlock(Long packageId, Integer quantity, String operationId, BlockException e) {
        return Response.fail("429", "package sold confirm busy, please try again later");
    }

    public Response<PackageStockChangeResponseDTO> rollbackPackageSoldBlock(Long packageId, Integer quantity, String operationId, BlockException e) {
        return Response.fail("429", "package sold rollback busy, please try again later");
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

    private PackageStockChangeResponseDTO toStockChangeResponse(PackageStockChangeResult source) {
        PackageStockChangeResponseDTO target = new PackageStockChangeResponseDTO();
        target.setPackageId(source.getPackageId());
        target.setQuantity(source.getQuantity());
        target.setStock(source.getStock());
        target.setSold(source.getSold());
        target.setChangeType(source.getChangeType());
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
}
