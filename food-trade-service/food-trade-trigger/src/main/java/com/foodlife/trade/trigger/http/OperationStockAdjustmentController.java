package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.OperationPackageStockAdjustRequestDTO;
import com.foodlife.trade.api.dto.OperationPackageStockAdjustResponseDTO;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustResult;
import com.foodlife.trade.trigger.app.OperationAuditApplicationService;
import com.foodlife.trade.trigger.app.OperationStockAdjustmentApplicationService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade/operations/package-stock-adjustments")
public class OperationStockAdjustmentController {

    private final OperationStockAdjustmentApplicationService operationStockAdjustmentApplicationService;
    private final OperationAuditApplicationService operationAuditApplicationService;

    public OperationStockAdjustmentController(OperationStockAdjustmentApplicationService operationStockAdjustmentApplicationService,
                                              OperationAuditApplicationService operationAuditApplicationService) {
        this.operationStockAdjustmentApplicationService = operationStockAdjustmentApplicationService;
        this.operationAuditApplicationService = operationAuditApplicationService;
    }

    @PostMapping
    public Response<OperationPackageStockAdjustResponseDTO> adjustPackageStock(@RequestBody OperationPackageStockAdjustRequestDTO request) {
        try {
            Long userId = UserHolder.getUserId();
            if (userId == null) {
                return Response.fail("401", "user not login");
            }
            OperationPackageStockAdjustResponseDTO response = toResponse(operationStockAdjustmentApplicationService.adjustPackageStock(toCommand(request, userId)));
            operationAuditApplicationService.recordSuccess("OPERATION_STOCK_ADJUST", "PACKAGE_STOCK", String.valueOf(response.getPackageId()),
                    request, response, "operation adjust package stock");
            return Response.success(response);
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        } catch (IllegalStateException e) {
            return Response.fail("503", e.getMessage());
        }
    }

    private OperationPackageStockAdjustCommand toCommand(OperationPackageStockAdjustRequestDTO request, Long userId) {
        OperationPackageStockAdjustCommand command = new OperationPackageStockAdjustCommand();
        command.setOperatorId(userId);
        if (request != null) {
            command.setPackageId(request.getPackageId());
            command.setAdjustQuantity(request.getAdjustQuantity());
            command.setReason(request.getReason());
            command.setOperationId(request.getOperationId());
        }
        return command;
    }

    private OperationPackageStockAdjustResponseDTO toResponse(OperationPackageStockAdjustResult source) {
        OperationPackageStockAdjustResponseDTO response = new OperationPackageStockAdjustResponseDTO();
        response.setOperationId(source.getOperationId());
        response.setOperatorId(source.getOperatorId());
        response.setPackageId(source.getPackageId());
        response.setAdjustQuantity(source.getAdjustQuantity());
        response.setStock(source.getStock());
        response.setSold(source.getSold());
        response.setTxStatus(source.getTxStatus());
        return response;
    }
}
