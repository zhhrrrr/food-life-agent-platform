package com.foodlife.trade.trigger.http;

import com.foodlife.trade.api.dto.GroupBuyTimeoutCompensationResponseDTO;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTimeoutCompensateDetail;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTimeoutCompensateResult;
import com.foodlife.trade.domain.order.groupbuy.service.GroupBuyTimeoutCompensationService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade/group-buy")
public class GroupBuyCompensationController {

    private final GroupBuyTimeoutCompensationService groupBuyTimeoutCompensationService;

    public GroupBuyCompensationController(GroupBuyTimeoutCompensationService groupBuyTimeoutCompensationService) {
        this.groupBuyTimeoutCompensationService = groupBuyTimeoutCompensationService;
    }

    @PostMapping("/timeout/compensate")
    public Response<GroupBuyTimeoutCompensationResponseDTO> compensateTimeoutTeams(@RequestParam(required = false) Integer limit) {
        try {
            GroupBuyTimeoutCompensateResult result = groupBuyTimeoutCompensationService.compensateTimeoutTeams(limit);
            return Response.success(toResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private GroupBuyTimeoutCompensationResponseDTO toResponse(GroupBuyTimeoutCompensateResult result) {
        GroupBuyTimeoutCompensationResponseDTO response = new GroupBuyTimeoutCompensationResponseDTO();
        response.setCompensateTime(result.getCompensateTime());
        response.setScannedTeamCount(result.getScannedTeamCount());
        response.setCompensatedTeamCount(result.getCompensatedTeamCount());
        response.setCanceledOrderCount(result.getCanceledOrderCount());
        response.setRefundedOrderCount(result.getRefundedOrderCount());
        response.setRestoredStockCount(result.getRestoredStockCount());
        response.setDetails(result.getDetails().stream().map(this::toDetail).collect(Collectors.toList()));
        return response;
    }

    private GroupBuyTimeoutCompensationResponseDTO.Detail toDetail(GroupBuyTimeoutCompensateDetail source) {
        GroupBuyTimeoutCompensationResponseDTO.Detail detail = new GroupBuyTimeoutCompensationResponseDTO.Detail();
        detail.setTeamId(source.getTeamId());
        detail.setActivityId(source.getActivityId());
        detail.setTeamStatus(source.getTeamStatus());
        detail.setBeforeLockCount(source.getBeforeLockCount());
        detail.setBeforeCompleteCount(source.getBeforeCompleteCount());
        detail.setCanceledOrderCount(source.getCanceledOrderCount());
        detail.setRefundedOrderCount(source.getRefundedOrderCount());
        detail.setRestoredStockCount(source.getRestoredStockCount());
        return detail;
    }
}
