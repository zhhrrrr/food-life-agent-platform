package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.GroupBuyTeamDetailResponseDTO;
import com.foodlife.trade.api.dto.GroupBuyTeamListResponseDTO;
import com.foodlife.trade.api.dto.UserGroupBuyOrderListResponseDTO;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyParticipantView;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamQueryResult;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamView;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyUserOrderQueryResult;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyUserOrderView;
import com.foodlife.trade.domain.order.groupbuy.service.GroupBuyQueryService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade/group-buy")
public class GroupBuyQueryController {

    private final GroupBuyQueryService groupBuyQueryService;

    public GroupBuyQueryController(GroupBuyQueryService groupBuyQueryService) {
        this.groupBuyQueryService = groupBuyQueryService;
    }

    @GetMapping("/teams")
    public Response<GroupBuyTeamListResponseDTO> queryAvailableTeams(@RequestParam Long packageId,
                                                                     @RequestParam(required = false) Integer limit) {
        try {
            GroupBuyTeamQueryResult result = groupBuyQueryService.queryAvailableTeams(packageId, limit);
            return Response.success(toTeamListResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/teams/{teamId}")
    public Response<GroupBuyTeamDetailResponseDTO> queryTeamDetail(@PathVariable String teamId) {
        try {
            GroupBuyTeamView result = groupBuyQueryService.queryTeamDetail(teamId);
            return Response.success(toTeamDetailResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("404", e.getMessage());
        }
    }

    @GetMapping("/orders")
    public Response<UserGroupBuyOrderListResponseDTO> queryUserGroupBuyOrders(@RequestParam(required = false) Long lastId,
                                                                              @RequestParam(required = false) Integer pageSize) {
        try {
            GroupBuyUserOrderQueryResult result = groupBuyQueryService.queryUserGroupBuyOrders(UserHolder.getUserId(), lastId, pageSize);
            return Response.success(toUserOrderListResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private GroupBuyTeamListResponseDTO toTeamListResponse(GroupBuyTeamQueryResult result) {
        GroupBuyTeamListResponseDTO response = new GroupBuyTeamListResponseDTO();
        response.setTeams(result.getTeams().stream().map(this::toTeamInfo).collect(Collectors.toList()));
        return response;
    }

    private GroupBuyTeamListResponseDTO.TeamInfo toTeamInfo(GroupBuyTeamView source) {
        GroupBuyTeamListResponseDTO.TeamInfo target = new GroupBuyTeamListResponseDTO.TeamInfo();
        target.setTeamId(source.getTeamId());
        target.setActivityId(source.getActivityId());
        target.setPackageId(source.getPackageId());
        target.setTargetCount(source.getTargetCount());
        target.setLockCount(source.getLockCount());
        target.setCompleteCount(source.getCompleteCount());
        target.setRemainingCount(source.getRemainingCount());
        target.setTeamStatus(source.getTeamStatus());
        target.setValidStartTime(source.getValidStartTime());
        target.setValidEndTime(source.getValidEndTime());
        target.setCanJoin(source.getCanJoin());
        return target;
    }

    private GroupBuyTeamDetailResponseDTO toTeamDetailResponse(GroupBuyTeamView source) {
        GroupBuyTeamDetailResponseDTO response = new GroupBuyTeamDetailResponseDTO();
        response.setTeamId(source.getTeamId());
        response.setActivityId(source.getActivityId());
        response.setPackageId(source.getPackageId());
        response.setTargetCount(source.getTargetCount());
        response.setLockCount(source.getLockCount());
        response.setCompleteCount(source.getCompleteCount());
        response.setRemainingCount(source.getRemainingCount());
        response.setTeamStatus(source.getTeamStatus());
        response.setValidStartTime(source.getValidStartTime());
        response.setValidEndTime(source.getValidEndTime());
        response.setCanJoin(source.getCanJoin());
        response.setParticipants(source.getParticipants().stream().map(this::toParticipantInfo).collect(Collectors.toList()));
        return response;
    }

    private GroupBuyTeamDetailResponseDTO.ParticipantInfo toParticipantInfo(GroupBuyParticipantView source) {
        GroupBuyTeamDetailResponseDTO.ParticipantInfo target = new GroupBuyTeamDetailResponseDTO.ParticipantInfo();
        target.setUserId(source.getUserId());
        target.setOrderId(source.getOrderId());
        target.setOrderNo(source.getOrderNo());
        target.setGroupBuyOrderStatus(source.getGroupBuyOrderStatus());
        target.setOrderStatus(source.getOrderStatus());
        target.setOutTradeTime(source.getOutTradeTime());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    private UserGroupBuyOrderListResponseDTO toUserOrderListResponse(GroupBuyUserOrderQueryResult result) {
        UserGroupBuyOrderListResponseDTO response = new UserGroupBuyOrderListResponseDTO();
        response.setOrders(result.getOrders().stream().map(this::toUserOrderInfo).collect(Collectors.toList()));
        response.setHasMore(result.getHasMore());
        response.setLastId(result.getLastId());
        return response;
    }

    private UserGroupBuyOrderListResponseDTO.OrderInfo toUserOrderInfo(GroupBuyUserOrderView source) {
        UserGroupBuyOrderListResponseDTO.OrderInfo target = new UserGroupBuyOrderListResponseDTO.OrderInfo();
        target.setGroupBuyOrderListId(source.getGroupBuyOrderListId());
        target.setUserId(source.getUserId());
        target.setTeamId(source.getTeamId());
        target.setOrderId(source.getOrderId());
        target.setOrderNo(source.getOrderNo());
        target.setActivityId(source.getActivityId());
        target.setPackageId(source.getPackageId());
        target.setPayAmount(source.getPayAmount());
        target.setGroupBuyOrderStatus(source.getGroupBuyOrderStatus());
        target.setOrderStatus(source.getOrderStatus());
        target.setTeamStatus(source.getTeamStatus());
        target.setTargetCount(source.getTargetCount());
        target.setLockCount(source.getLockCount());
        target.setCompleteCount(source.getCompleteCount());
        target.setRemainingCount(source.getRemainingCount());
        target.setOutTradeTime(source.getOutTradeTime());
        target.setValidEndTime(source.getValidEndTime());
        target.setCreateTime(source.getCreateTime());
        target.setCanPay(source.getCanPay());
        target.setCanCancel(source.getCanCancel());
        target.setCanRefund(source.getCanRefund());
        return target;
    }
}
