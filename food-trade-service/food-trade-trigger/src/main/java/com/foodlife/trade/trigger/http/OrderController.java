package com.foodlife.trade.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.trade.api.dto.CancelOrderResponseDTO;
import com.foodlife.trade.api.dto.CreateGroupBuyOrderRequestDTO;
import com.foodlife.trade.api.dto.CreateGroupBuyOrderResponseDTO;
import com.foodlife.trade.api.dto.CreateOrderRequestDTO;
import com.foodlife.trade.api.dto.CreateOrderResponseDTO;
import com.foodlife.trade.api.dto.CreateSeckillOrderRequestDTO;
import com.foodlife.trade.api.dto.CreateSeckillOrderRequestResponseDTO;
import com.foodlife.trade.api.dto.CreateSeckillOrderResponseDTO;
import com.foodlife.trade.api.dto.OrderDetailResponseDTO;
import com.foodlife.trade.api.dto.OrderItemResponseDTO;
import com.foodlife.trade.api.dto.OrderListResponseDTO;
import com.foodlife.trade.api.dto.PayOrderRequestDTO;
import com.foodlife.trade.api.dto.PayOrderResponseDTO;
import com.foodlife.trade.api.dto.RefundOrderRequestDTO;
import com.foodlife.trade.api.dto.RefundOrderResponseDTO;
import com.foodlife.trade.api.dto.SeckillActivityListResponseDTO;
import com.foodlife.trade.api.dto.SeckillOrderRequestProcessResponseDTO;
import com.foodlife.trade.api.dto.SeckillOrderRequestQueryResponseDTO;
import com.foodlife.trade.api.dto.SeckillOrderRequestRecoveryResponseDTO;
import com.foodlife.trade.api.dto.SeckillOrderTraceResponseDTO;
import com.foodlife.trade.api.dto.SeckillStockReconcileResponseDTO;
import com.foodlife.trade.api.dto.SeckillStockPreheatResponseDTO;
import com.foodlife.trade.api.dto.UseOrderResponseDTO;
import com.foodlife.trade.domain.order.model.CancelOrderResult;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockOrderCommand;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityView;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderCommand;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestProcessResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestRecoveryResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderTraceEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockReconcileResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockPreheatResult;
import com.foodlife.trade.domain.order.model.OrderDetailEntity;
import com.foodlife.trade.domain.order.model.OrderListResult;
import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import com.foodlife.trade.domain.order.model.OrderPaySuccessEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSummaryEntity;
import com.foodlife.trade.domain.order.model.OrderUseCommandEntity;
import com.foodlife.trade.domain.order.model.OrderUseResult;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityEntity;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade")
public class OrderController {

    private final OrderDomainService orderDomainService;

    public OrderController(OrderDomainService orderDomainService) {
        this.orderDomainService = orderDomainService;
    }

    @PostMapping("/orders/normal")
    public Response<CreateOrderResponseDTO> createNormalOrder(@RequestBody CreateOrderRequestDTO request) {
        try {
            CreateOrderResult result = orderDomainService.createNormalOrder(toCommand(request));
            return Response.success(toResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/group-buy")
    public Response<CreateGroupBuyOrderResponseDTO> createGroupBuyOrder(@RequestBody CreateGroupBuyOrderRequestDTO request) {
        try {
            GroupBuyLockResult result = orderDomainService.createGroupBuyOrder(toGroupBuyCommand(request));
            return Response.success(toGroupBuyResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/seckill/activities")
    public Response<SeckillActivityListResponseDTO> querySeckillActivities(@RequestParam(required = false) Long packageId,
                                                                           @RequestParam(required = false) Integer limit) {
        try {
            List<SeckillActivityView> result = orderDomainService.querySeckillActivities(packageId, limit);
            return Response.success(toSeckillActivityListResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/seckill")
    public Response<CreateSeckillOrderResponseDTO> createSeckillOrder(@RequestBody CreateSeckillOrderRequestDTO request) {
        try {
            SeckillOrderResult result = orderDomainService.createSeckillOrder(toSeckillCommand(request));
            return Response.success(toSeckillOrderResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/seckill/async")
    public Response<CreateSeckillOrderRequestResponseDTO> createSeckillOrderAsync(@RequestBody CreateSeckillOrderRequestDTO request) {
        try {
            SeckillOrderRequestResult result = orderDomainService.createSeckillOrderRequest(toSeckillCommand(request));
            return Response.success(toSeckillOrderRequestResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/seckill/order-requests/{requestNo}")
    public Response<SeckillOrderRequestQueryResponseDTO> querySeckillOrderRequest(@PathVariable String requestNo) {
        try {
            SeckillOrderRequestResult result = orderDomainService.querySeckillOrderRequest(requestNo, UserHolder.getUserId());
            return Response.success(toSeckillOrderRequestQueryResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("404", e.getMessage());
        }
    }

    @GetMapping("/seckill/order-requests/{requestNo}/trace")
    public Response<SeckillOrderTraceResponseDTO> querySeckillOrderTraceByRequestNo(@PathVariable String requestNo) {
        try {
            SeckillOrderTraceEntity result = orderDomainService.querySeckillOrderTraceByRequestNo(requestNo, UserHolder.getUserId());
            return Response.success(toSeckillOrderTraceResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("404", e.getMessage());
        }
    }

    @GetMapping("/orders/{orderId}/seckill-trace")
    public Response<SeckillOrderTraceResponseDTO> querySeckillOrderTraceByOrderId(@PathVariable Long orderId) {
        try {
            SeckillOrderTraceEntity result = orderDomainService.querySeckillOrderTraceByOrderId(orderId, UserHolder.getUserId());
            return Response.success(toSeckillOrderTraceResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("404", e.getMessage());
        }
    }

    @PostMapping("/seckill/order-requests/process")
    public Response<SeckillOrderRequestProcessResponseDTO> processSeckillOrderRequests(@RequestParam(required = false) Integer limit) {
        SeckillOrderRequestProcessResult result = orderDomainService.processPendingSeckillOrderRequests(limit);
        return Response.success(toSeckillOrderRequestProcessResponse(result));
    }

    @PostMapping("/seckill/order-requests/recover")
    public Response<SeckillOrderRequestRecoveryResponseDTO> recoverSeckillOrderRequests(@RequestParam(required = false) Integer limit) {
        SeckillOrderRequestRecoveryResult result = orderDomainService.recoverSeckillOrderRequests(limit);
        return Response.success(toSeckillOrderRequestRecoveryResponse(result));
    }

    @PostMapping("/seckill/activities/{activityId}/stock/reconcile")
    public Response<SeckillStockReconcileResponseDTO> reconcileSeckillStock(@PathVariable Long activityId) {
        try {
            SeckillStockReconcileResult result = orderDomainService.reconcileSeckillActivityStock(activityId);
            return Response.success(toSeckillStockReconcileResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/seckill/activities/{activityId}/stock/preheat")
    public Response<SeckillStockPreheatResponseDTO> preheatSeckillStock(@PathVariable Long activityId) {
        try {
            SeckillStockPreheatResult result = orderDomainService.preheatSeckillActivityStock(activityId);
            return Response.success(toSeckillStockPreheatResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/orders/{orderId}")
    public Response<OrderDetailResponseDTO> queryOrderDetail(@PathVariable Long orderId) {
        try {
            OrderDetailEntity detail = orderDomainService.queryOrderDetail(orderId, UserHolder.getUserId());
            return Response.success(toDetailResponse(detail));
        } catch (IllegalArgumentException e) {
            return Response.fail("404", e.getMessage());
        }
    }

    @GetMapping("/orders")
    public Response<OrderListResponseDTO> queryUserOrderList(@RequestParam(required = false) Long lastId,
                                                             @RequestParam(required = false) Integer pageSize) {
        try {
            OrderListResult result = orderDomainService.queryUserOrderList(UserHolder.getUserId(), lastId, pageSize);
            return Response.success(toOrderListResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Response<CancelOrderResponseDTO> cancelOrder(@PathVariable Long orderId) {
        try {
            CancelOrderResult result = orderDomainService.cancelOrder(orderId, UserHolder.getUserId());
            return Response.success(toCancelResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/{orderId}/pay/mock")
    public Response<PayOrderResponseDTO> payOrderMock(@PathVariable Long orderId,
                                                      @RequestBody(required = false) PayOrderRequestDTO request) {
        try {
            OrderPaySettlementEntity result = orderDomainService.payOrderMock(toPaySuccessEntity(orderId, request));
            return Response.success(toPayResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/{orderId}/refund/mock")
    public Response<RefundOrderResponseDTO> refundOrderMock(@PathVariable Long orderId,
                                                            @RequestBody(required = false) RefundOrderRequestDTO request) {
        try {
            OrderRefundBehaviorEntity result = orderDomainService.refundOrderMock(toRefundCommand(orderId, request));
            return Response.success(toRefundResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @PostMapping("/orders/{orderId}/use/mock")
    public Response<UseOrderResponseDTO> useOrderMock(@PathVariable Long orderId) {
        try {
            OrderUseResult result = orderDomainService.useOrderMock(toUseCommand(orderId));
            return Response.success(toUseResponse(result));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private CreateOrderCommand toCommand(CreateOrderRequestDTO request) {
        CreateOrderCommand command = new CreateOrderCommand();
        command.setUserId(UserHolder.getUserId());
        command.setPackageId(request == null ? null : request.getPackageId());
        command.setQuantity(request == null ? null : request.getQuantity());
        command.setUserCouponId(request == null ? null : request.getUserCouponId());
        return command;
    }

    private GroupBuyLockOrderCommand toGroupBuyCommand(CreateGroupBuyOrderRequestDTO request) {
        GroupBuyLockOrderCommand command = new GroupBuyLockOrderCommand();
        command.setUserId(UserHolder.getUserId());
        command.setPackageId(request == null ? null : request.getPackageId());
        command.setQuantity(request == null ? null : request.getQuantity());
        command.setTeamId(request == null ? null : request.getTeamId());
        return command;
    }

    private SeckillOrderCommand toSeckillCommand(CreateSeckillOrderRequestDTO request) {
        SeckillOrderCommand command = new SeckillOrderCommand();
        command.setUserId(UserHolder.getUserId());
        command.setActivityId(request == null ? null : request.getActivityId());
        command.setQuantity(request == null ? null : request.getQuantity());
        return command;
    }

    private CreateOrderResponseDTO toResponse(CreateOrderResult result) {
        CreateOrderResponseDTO response = new CreateOrderResponseDTO();
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setTotalAmount(result.getTotalAmount());
        response.setDiscountAmount(result.getDiscountAmount());
        response.setPayAmount(result.getPayAmount());
        response.setUserCouponId(result.getUserCouponId());
        response.setOrderStatus(result.getOrderStatus());
        return response;
    }

    private CreateGroupBuyOrderResponseDTO toGroupBuyResponse(GroupBuyLockResult result) {
        CreateGroupBuyOrderResponseDTO response = new CreateGroupBuyOrderResponseDTO();
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setTeamId(result.getTeamId());
        response.setActivityId(result.getActivityId());
        response.setPayAmount(result.getPayAmount());
        response.setOrderStatus(result.getOrderStatus());
        response.setTeamStatus(result.getTeamStatus());
        response.setTargetCount(result.getTargetCount());
        response.setLockCount(result.getLockCount());
        response.setCompleteCount(result.getCompleteCount());
        return response;
    }

    private SeckillActivityListResponseDTO toSeckillActivityListResponse(List<SeckillActivityView> activities) {
        SeckillActivityListResponseDTO response = new SeckillActivityListResponseDTO();
        response.setActivities(activities.stream().map(this::toSeckillActivityInfo).collect(Collectors.toList()));
        return response;
    }

    private SeckillActivityListResponseDTO.ActivityInfo toSeckillActivityInfo(SeckillActivityView source) {
        SeckillActivityListResponseDTO.ActivityInfo target = new SeckillActivityListResponseDTO.ActivityInfo();
        target.setActivityId(source.getActivityId());
        target.setPackageId(source.getPackageId());
        target.setActivityName(source.getActivityName());
        target.setSeckillPrice(source.getSeckillPrice());
        target.setActivityStatus(source.getActivityStatus());
        target.setValidStartTime(source.getValidStartTime());
        target.setValidEndTime(source.getValidEndTime());
        target.setStock(source.getStock());
        target.setUserTakeLimit(source.getUserTakeLimit());
        target.setCanBuy(source.getCanBuy());
        return target;
    }

    private CreateSeckillOrderResponseDTO toSeckillOrderResponse(SeckillOrderResult result) {
        CreateSeckillOrderResponseDTO response = new CreateSeckillOrderResponseDTO();
        response.setActivityId(result.getActivityId());
        response.setPackageId(result.getPackageId());
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setPayAmount(result.getPayAmount());
        response.setOrderStatus(result.getOrderStatus());
        response.setRemainingStock(result.getRemainingStock());
        return response;
    }

    private CreateSeckillOrderRequestResponseDTO toSeckillOrderRequestResponse(SeckillOrderRequestResult result) {
        CreateSeckillOrderRequestResponseDTO response = new CreateSeckillOrderRequestResponseDTO();
        response.setRequestNo(result.getRequestNo());
        response.setActivityId(result.getActivityId());
        response.setPackageId(result.getPackageId());
        response.setQuantity(result.getQuantity());
        response.setRequestStatus(result.getRequestStatus());
        response.setRemainingStock(result.getRemainingStock());
        return response;
    }

    private SeckillOrderRequestQueryResponseDTO toSeckillOrderRequestQueryResponse(SeckillOrderRequestResult result) {
        SeckillOrderRequestQueryResponseDTO response = new SeckillOrderRequestQueryResponseDTO();
        response.setRequestNo(result.getRequestNo());
        response.setUserId(result.getUserId());
        response.setActivityId(result.getActivityId());
        response.setPackageId(result.getPackageId());
        response.setQuantity(result.getQuantity());
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setRequestStatus(result.getRequestStatus());
        response.setFailReason(result.getFailReason());
        return response;
    }

    private SeckillOrderTraceResponseDTO toSeckillOrderTraceResponse(SeckillOrderTraceEntity result) {
        SeckillOrderTraceResponseDTO response = new SeckillOrderTraceResponseDTO();
        response.setRequest(toTraceRequestInfo(result));
        response.setOrder(toTraceOrderInfo(result));
        response.setActivity(toTraceActivityInfo(result.getActivity()));
        response.setPackageInfo(toTracePackageInfo(result.getPackageSnapshot()));
        response.setStock(toTraceStockInfo(result));
        response.setCurrentStage(result.getCurrentStage());
        return response;
    }

    private SeckillOrderTraceResponseDTO.RequestInfo toTraceRequestInfo(SeckillOrderTraceEntity result) {
        SeckillOrderRequestResult request = toRequestResult(result);
        SeckillOrderTraceResponseDTO.RequestInfo info = new SeckillOrderTraceResponseDTO.RequestInfo();
        info.setRequestNo(request.getRequestNo());
        info.setUserId(request.getUserId());
        info.setActivityId(request.getActivityId());
        info.setPackageId(request.getPackageId());
        info.setQuantity(request.getQuantity());
        info.setOrderId(request.getOrderId());
        info.setOrderNo(request.getOrderNo());
        info.setRequestStatus(request.getRequestStatus());
        info.setFailReason(request.getFailReason());
        if (result.getRequest() != null) {
            info.setCreateTime(result.getRequest().getCreateTime());
            info.setUpdateTime(result.getRequest().getUpdateTime());
        }
        return info;
    }

    private SeckillOrderRequestResult toRequestResult(SeckillOrderTraceEntity result) {
        SeckillOrderRequestResult request = new SeckillOrderRequestResult();
        if (result.getRequest() == null) {
            return request;
        }
        request.setRequestNo(result.getRequest().getRequestNo());
        request.setUserId(result.getRequest().getUserId());
        request.setActivityId(result.getRequest().getActivityId());
        request.setPackageId(result.getRequest().getPackageId());
        request.setQuantity(result.getRequest().getQuantity());
        request.setOrderId(result.getRequest().getOrderId());
        request.setOrderNo(result.getRequest().getOrderNo());
        request.setRequestStatus(result.getRequest().getRequestStatus());
        request.setFailReason(result.getRequest().getFailReason());
        return request;
    }

    private SeckillOrderTraceResponseDTO.OrderInfo toTraceOrderInfo(SeckillOrderTraceEntity result) {
        DiningOrderEntity order = result.getOrder();
        if (order == null) {
            return null;
        }
        SeckillOrderTraceResponseDTO.OrderInfo info = new SeckillOrderTraceResponseDTO.OrderInfo();
        info.setOrderId(order.getId());
        info.setOrderNo(order.getOrderNo());
        info.setUserId(order.getUserId());
        info.setShopId(order.getShopId());
        info.setPackageId(order.getPackageId());
        info.setQuantity(order.getQuantity());
        info.setTotalAmount(order.getTotalAmount());
        info.setPayAmount(order.getPayAmount());
        info.setTradeType(order.getTradeType());
        info.setOrderStatus(order.getOrderStatus());
        info.setUseTime(order.getUseTime());
        info.setCreateTime(order.getCreateTime());
        info.setItems(result.getOrderItems().stream().map(this::toItemResponse).collect(Collectors.toList()));
        return info;
    }

    private SeckillOrderTraceResponseDTO.ActivityInfo toTraceActivityInfo(SeckillActivityEntity activity) {
        if (activity == null) {
            return null;
        }
        SeckillOrderTraceResponseDTO.ActivityInfo info = new SeckillOrderTraceResponseDTO.ActivityInfo();
        info.setActivityId(activity.getId());
        info.setPackageId(activity.getPackageId());
        info.setActivityName(activity.getActivityName());
        info.setSeckillPrice(activity.getSeckillPrice());
        info.setActivityStatus(activity.getActivityStatus());
        info.setValidStartTime(activity.getValidStartTime());
        info.setValidEndTime(activity.getValidEndTime());
        info.setUserTakeLimit(activity.getUserTakeLimit());
        return info;
    }

    private SeckillOrderTraceResponseDTO.PackageInfo toTracePackageInfo(PackageTradeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        SeckillOrderTraceResponseDTO.PackageInfo info = new SeckillOrderTraceResponseDTO.PackageInfo();
        info.setShopId(snapshot.getShopId());
        info.setShopName(snapshot.getShopName());
        info.setPackageId(snapshot.getPackageId());
        info.setPackageName(snapshot.getPackageName());
        info.setPackageDescription(snapshot.getPackageDescription());
        info.setCoverImage(snapshot.getCoverImage());
        info.setPrice(snapshot.getPrice());
        info.setOriginalPrice(snapshot.getOriginalPrice());
        info.setStock(snapshot.getStock());
        info.setPackageStatus(snapshot.getPackageStatus());
        info.setUseRule(snapshot.getUseRule());
        return info;
    }

    private SeckillOrderTraceResponseDTO.StockInfo toTraceStockInfo(SeckillOrderTraceEntity result) {
        SeckillOrderTraceResponseDTO.StockInfo info = new SeckillOrderTraceResponseDTO.StockInfo();
        info.setDbStock(result.getDbStock());
        info.setRedisStock(result.getRedisStock());
        info.setWaitPayCount(result.getWaitPayCount());
        info.setPaidCount(result.getPaidCount());
        return info;
    }

    private SeckillOrderRequestProcessResponseDTO toSeckillOrderRequestProcessResponse(SeckillOrderRequestProcessResult result) {
        SeckillOrderRequestProcessResponseDTO response = new SeckillOrderRequestProcessResponseDTO();
        response.setScannedCount(result.getScannedCount());
        response.setSuccessCount(result.getSuccessCount());
        response.setFailedCount(result.getFailedCount());
        response.setRetryCount(result.getRetryCount());
        return response;
    }

    private SeckillOrderRequestRecoveryResponseDTO toSeckillOrderRequestRecoveryResponse(SeckillOrderRequestRecoveryResult result) {
        SeckillOrderRequestRecoveryResponseDTO response = new SeckillOrderRequestRecoveryResponseDTO();
        response.setScannedMessageCount(result.getScannedMessageCount());
        response.setRecoveredMessageCount(result.getRecoveredMessageCount());
        response.setCanceledRequestCount(result.getCanceledRequestCount());
        response.setReleasedStockCount(result.getReleasedStockCount());
        return response;
    }

    private SeckillStockReconcileResponseDTO toSeckillStockReconcileResponse(SeckillStockReconcileResult result) {
        SeckillStockReconcileResponseDTO response = new SeckillStockReconcileResponseDTO();
        response.setActivityId(result.getActivityId());
        response.setDbStock(result.getDbStock());
        response.setRedisStockBefore(result.getRedisStockBefore());
        response.setRedisStockAfter(result.getRedisStockAfter());
        response.setWaitPayCount(result.getWaitPayCount());
        response.setPaidCount(result.getPaidCount());
        response.setRefreshed(result.getRefreshed());
        return response;
    }

    private SeckillStockPreheatResponseDTO toSeckillStockPreheatResponse(SeckillStockPreheatResult result) {
        SeckillStockPreheatResponseDTO response = new SeckillStockPreheatResponseDTO();
        response.setActivityId(result.getActivityId());
        response.setDbStock(result.getDbStock());
        response.setRedisStock(result.getRedisStock());
        response.setStockKey(result.getStockKey());
        response.setUserKey(result.getUserKey());
        return response;
    }

    private CancelOrderResponseDTO toCancelResponse(CancelOrderResult result) {
        CancelOrderResponseDTO response = new CancelOrderResponseDTO();
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setOrderStatus(result.getOrderStatus());
        return response;
    }

    private OrderPaySuccessEntity toPaySuccessEntity(Long orderId, PayOrderRequestDTO request) {
        OrderPaySuccessEntity entity = new OrderPaySuccessEntity();
        entity.setSource(readOrDefault(request == null ? null : request.getSource(), "FOOD_LIFE"));
        entity.setChannel(readOrDefault(request == null ? null : request.getChannel(), "MOCK_PAY"));
        entity.setUserId(UserHolder.getUserId());
        entity.setOrderId(orderId);
        entity.setOutTradeNo(readOrDefault(request == null ? null : request.getOutTradeNo(), "MOCK" + System.currentTimeMillis()));
        entity.setOutTradeTime(LocalDateTime.now());
        return entity;
    }

    private PayOrderResponseDTO toPayResponse(OrderPaySettlementEntity result) {
        PayOrderResponseDTO response = new PayOrderResponseDTO();
        response.setSource(result.getSource());
        response.setChannel(result.getChannel());
        response.setUserId(result.getUserId());
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setOrderStatus(result.getOrderStatus());
        response.setOutTradeNo(result.getOutTradeNo());
        response.setOutTradeTime(result.getOutTradeTime());
        response.setTeamId(result.getTeamId());
        response.setActivityId(result.getActivityId());
        response.setTeamStatus(result.getTeamStatus());
        response.setTargetCount(result.getTargetCount());
        response.setLockCount(result.getLockCount());
        response.setCompleteCount(result.getCompleteCount());
        return response;
    }

    private OrderRefundCommandEntity toRefundCommand(Long orderId, RefundOrderRequestDTO request) {
        OrderRefundCommandEntity command = new OrderRefundCommandEntity();
        command.setSource(readOrDefault(request == null ? null : request.getSource(), "FOOD_LIFE"));
        command.setChannel(readOrDefault(request == null ? null : request.getChannel(), "MOCK_REFUND"));
        command.setUserId(UserHolder.getUserId());
        command.setOrderId(orderId);
        return command;
    }

    private RefundOrderResponseDTO toRefundResponse(OrderRefundBehaviorEntity result) {
        RefundOrderResponseDTO response = new RefundOrderResponseDTO();
        response.setSource(result.getSource());
        response.setChannel(result.getChannel());
        response.setUserId(result.getUserId());
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setOrderStatus(result.getOrderStatus());
        response.setRefundBehavior(result.getRefundBehavior().getCode());
        response.setTeamId(result.getTeamId());
        response.setActivityId(result.getActivityId());
        response.setTeamStatus(result.getTeamStatus());
        response.setTargetCount(result.getTargetCount());
        response.setLockCount(result.getLockCount());
        response.setCompleteCount(result.getCompleteCount());
        return response;
    }

    private OrderUseCommandEntity toUseCommand(Long orderId) {
        OrderUseCommandEntity command = new OrderUseCommandEntity();
        command.setUserId(UserHolder.getUserId());
        command.setOrderId(orderId);
        return command;
    }

    private UseOrderResponseDTO toUseResponse(OrderUseResult result) {
        UseOrderResponseDTO response = new UseOrderResponseDTO();
        response.setUserId(result.getUserId());
        response.setOrderId(result.getOrderId());
        response.setOrderNo(result.getOrderNo());
        response.setOrderStatus(result.getOrderStatus());
        response.setUseBehavior(result.getUseBehavior());
        response.setUseTime(result.getUseTime());
        return response;
    }

    private String readOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private OrderListResponseDTO toOrderListResponse(OrderListResult result) {
        OrderListResponseDTO response = new OrderListResponseDTO();
        response.setOrders(result.getOrders().stream().map(this::toOrderInfoResponse).collect(Collectors.toList()));
        response.setHasMore(result.getHasMore());
        response.setLastId(result.getLastId());
        return response;
    }

    private OrderListResponseDTO.OrderInfo toOrderInfoResponse(OrderSummaryEntity order) {
        OrderListResponseDTO.OrderInfo response = new OrderListResponseDTO.OrderInfo();
        response.setOrderId(order.getOrderId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setShopId(order.getShopId());
        response.setShopNameSnapshot(order.getShopNameSnapshot());
        response.setPackageId(order.getPackageId());
        response.setPackageNameSnapshot(order.getPackageNameSnapshot());
        response.setCoverImageSnapshot(order.getCoverImageSnapshot());
        response.setQuantity(order.getQuantity());
        response.setTotalAmount(order.getTotalAmount());
        response.setDiscountAmount(order.getDiscountAmount());
        response.setPayAmount(order.getPayAmount());
        response.setUserCouponId(order.getUserCouponId());
        response.setTradeType(order.getTradeType());
        response.setOrderStatus(order.getOrderStatus());
        response.setUseTime(order.getUseTime());
        response.setCreateTime(order.getCreateTime());
        return response;
    }

    private OrderDetailResponseDTO toDetailResponse(OrderDetailEntity detail) {
        DiningOrderEntity order = detail.getOrder();
        OrderDetailResponseDTO response = new OrderDetailResponseDTO();
        response.setOrderId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setShopId(order.getShopId());
        response.setPackageId(order.getPackageId());
        response.setQuantity(order.getQuantity());
        response.setTotalAmount(order.getTotalAmount());
        response.setDiscountAmount(order.getDiscountAmount());
        response.setPayAmount(order.getPayAmount());
        response.setUserCouponId(order.getUserCouponId());
        response.setTradeType(order.getTradeType());
        response.setOrderStatus(order.getOrderStatus());
        response.setUseTime(order.getUseTime());
        response.setCreateTime(order.getCreateTime());
        response.setItems(toItemResponses(detail.getItems()));
        return response;
    }

    private List<OrderItemResponseDTO> toItemResponses(List<DiningOrderItemEntity> items) {
        return items.stream().map(this::toItemResponse).collect(Collectors.toList());
    }

    private OrderItemResponseDTO toItemResponse(DiningOrderItemEntity item) {
        OrderItemResponseDTO response = new OrderItemResponseDTO();
        response.setItemId(item.getId());
        response.setShopId(item.getShopId());
        response.setShopNameSnapshot(item.getShopNameSnapshot());
        response.setPackageId(item.getPackageId());
        response.setPackageNameSnapshot(item.getPackageNameSnapshot());
        response.setPackageDescriptionSnapshot(item.getPackageDescriptionSnapshot());
        response.setCoverImageSnapshot(item.getCoverImageSnapshot());
        response.setPackagePriceSnapshot(item.getPackagePriceSnapshot());
        response.setActualPrice(item.getActualPrice());
        response.setQuantity(item.getQuantity());
        response.setUseRuleSnapshot(item.getUseRuleSnapshot());
        return response;
    }
}
