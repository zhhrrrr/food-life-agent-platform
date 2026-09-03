package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.SeataPackageStockAdjustRequestDTO;
import com.foodlife.trade.api.dto.SeataPackageStockAdjustResponseDTO;
import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustResult;
import com.foodlife.trade.trigger.app.SeataDemoApplicationService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade/demo/seata")
public class SeataDemoController {

    private final SeataDemoApplicationService seataDemoApplicationService;

    public SeataDemoController(SeataDemoApplicationService seataDemoApplicationService) {
        this.seataDemoApplicationService = seataDemoApplicationService;
    }

    @PostMapping("/package-stock-adjust")
    public Response<SeataPackageStockAdjustResponseDTO> adjustPackageStock(@RequestBody SeataPackageStockAdjustRequestDTO request) {
        try {
            Long userId = UserHolder.getUserId();
            if (userId == null) {
                return Response.fail("401", "user not login");
            }
            return Response.success(toResponse(seataDemoApplicationService.adjustPackageStock(toCommand(request, userId))));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        } catch (IllegalStateException e) {
            return Response.fail("503", e.getMessage());
        }
    }

    private DistributedPackageStockAdjustCommand toCommand(SeataPackageStockAdjustRequestDTO request, Long userId) {
        DistributedPackageStockAdjustCommand command = new DistributedPackageStockAdjustCommand();
        command.setOperatorId(userId);
        if (request != null) {
            command.setPackageId(request.getPackageId());
            command.setAdjustQuantity(request.getAdjustQuantity());
            command.setReason(request.getReason());
            command.setOperationId(request.getOperationId());
        }
        return command;
    }

    private SeataPackageStockAdjustResponseDTO toResponse(DistributedPackageStockAdjustResult source) {
        SeataPackageStockAdjustResponseDTO response = new SeataPackageStockAdjustResponseDTO();
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
