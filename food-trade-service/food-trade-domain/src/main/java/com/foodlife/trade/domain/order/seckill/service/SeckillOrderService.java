package com.foodlife.trade.domain.order.seckill.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.event.OrderTimeoutCloseMessage;
import com.foodlife.trade.domain.order.event.TradeMqTopics;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.message.constant.LocalMessageStatusConstants;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.seckill.constant.SeckillRequestStatusConstants;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityView;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderAggregate;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderCommand;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestProcessResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestRecoveryResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderTraceEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockReconcileResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockOccupyResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockPreheatResult;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SeckillOrderService {

    private static final int DEFAULT_ACTIVITY_LIMIT = 20;
    private static final int MAX_ACTIVITY_LIMIT = 50;
    private static final int DEFAULT_PROCESS_LIMIT = 20;
    private static final int MAX_PROCESS_LIMIT = 100;
    private static final int DEFAULT_RECOVERY_LIMIT = 20;
    private static final int MAX_RECOVERY_LIMIT = 100;
    private static final int MAX_MESSAGE_RETRY_COUNT = 3;
    private static final int DEFAULT_PROCESSING_STUCK_SECONDS = 120;
    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 300;
    private static final String MESSAGE_TYPE_SECKILL_ORDER_CREATE = "SECKILL_ORDER_CREATE";
    private static final String BIZ_TYPE_SECKILL_ORDER_REQUEST = "SECKILL_ORDER_REQUEST";

    private final ISeckillRepository seckillRepository;
    private final ISeckillStockRepository seckillStockRepository;
    private final IOrderRepository orderRepository;
    private final IBusinessPackagePort businessPackagePort;
    private final OrderFactory orderFactory;
    private final ITradeEventPublisher tradeEventPublisher;

    public SeckillOrderService(ISeckillRepository seckillRepository,
                               ISeckillStockRepository seckillStockRepository,
                               IOrderRepository orderRepository,
                               IBusinessPackagePort businessPackagePort,
                               OrderFactory orderFactory,
                               ITradeEventPublisher tradeEventPublisher) {
        this.seckillRepository = seckillRepository;
        this.seckillStockRepository = seckillStockRepository;
        this.orderRepository = orderRepository;
        this.businessPackagePort = businessPackagePort;
        this.orderFactory = orderFactory;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    public List<SeckillActivityView> queryAvailableActivities(Long packageId, Integer limit) {
        return seckillRepository.listAvailableActivities(packageId, LocalDateTime.now(), normalizeLimit(limit))
                .stream()
                .map(this::fillRedisStock)
                .collect(Collectors.toList());
    }

    public SeckillStockPreheatResult preheatActivityStock(Long activityId) {
        if (activityId == null) {
            throw new IllegalArgumentException("activityId required");
        }
        LocalDateTime now = LocalDateTime.now();
        SeckillActivityEntity activity = seckillRepository.queryActivityById(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("seckill activity not found");
        }
        return seckillStockRepository.preheatActivityStock(activity, now);
    }

    public SeckillOrderResult createSeckillOrder(SeckillOrderCommand command) {
        boolean stockOccupied = false;
        SeckillActivityEntity activity = null;
        try {
            checkCommand(command);
            LocalDateTime now = LocalDateTime.now();
            activity = queryAndCheckActivity(command, now);
            checkUserTakeLimit(command, activity);
            SeckillStockOccupyResult stockOccupyResult = occupyActivityStock(activity, command.getUserId(), now);
            stockOccupied = true;

            PackageTradeSnapshot snapshot = queryAndCheckSnapshot(activity);

            SeckillOrderAggregate aggregate = buildAggregate(command, activity, snapshot);
            SeckillOrderResult result = seckillRepository.saveSeckillOrder(aggregate, now);
            result.setRemainingStock(stockOccupyResult.getRemainingStock());
            return result;
        } catch (IllegalArgumentException e) {
            releaseOccupiedStock(stockOccupied, activity, command);
            throw e;
        } catch (IllegalStateException e) {
            releaseOccupiedStock(stockOccupied, activity, command);
            throw e;
        } catch (Exception e) {
            releaseOccupiedStock(stockOccupied, activity, command);
            throw new IllegalStateException("seckill order create failed", e);
        }
    }

    public SeckillOrderRequestResult createSeckillOrderRequest(SeckillOrderCommand command) {
        boolean stockOccupied = false;
        SeckillActivityEntity activity = null;
        try {
            checkCommand(command);
            LocalDateTime now = LocalDateTime.now();
            activity = queryAndCheckActivity(command, now);
            checkUserTakeLimit(command, activity);
            SeckillStockOccupyResult stockOccupyResult = occupyActivityStock(activity, command.getUserId(), now);
            stockOccupied = true;

            String requestNo = generateRequestNo(command.getUserId());
            SeckillOrderRequestEntity request = buildOrderRequest(command, activity, requestNo, now);
            TradeLocalMessageEntity message = buildLocalMessage(request, now);
            seckillRepository.saveSeckillOrderRequestAndMessage(request, message);

            SeckillOrderRequestResult result = toRequestResult(request);
            result.setRemainingStock(stockOccupyResult.getRemainingStock());
            return result;
        } catch (IllegalArgumentException e) {
            releaseOccupiedStock(stockOccupied, activity, command);
            throw e;
        } catch (IllegalStateException e) {
            releaseOccupiedStock(stockOccupied, activity, command);
            throw e;
        } catch (Exception e) {
            releaseOccupiedStock(stockOccupied, activity, command);
            throw new IllegalStateException("seckill order request create failed", e);
        }
    }

    public SeckillOrderRequestResult querySeckillOrderRequest(String requestNo, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (requestNo == null || requestNo.trim().isEmpty()) {
            throw new IllegalArgumentException("requestNo required");
        }
        SeckillOrderRequestEntity request = seckillRepository.querySeckillOrderRequest(requestNo.trim());
        if (request == null || !userId.equals(request.getUserId())) {
            throw new IllegalArgumentException("seckill order request not found");
        }
        return toRequestResult(request);
    }

    public SeckillOrderTraceEntity queryOrderTraceByRequestNo(String requestNo, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (requestNo == null || requestNo.trim().isEmpty()) {
            throw new IllegalArgumentException("requestNo required");
        }
        SeckillOrderRequestEntity request = seckillRepository.querySeckillOrderRequest(requestNo.trim());
        if (request == null || !userId.equals(request.getUserId())) {
            throw new IllegalArgumentException("seckill order request not found");
        }
        return buildTrace(request);
    }

    public SeckillOrderTraceEntity queryOrderTraceByOrderId(Long orderId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId required");
        }
        SeckillOrderRequestEntity request = seckillRepository.querySeckillOrderRequestByOrderId(orderId);
        if (request == null || !userId.equals(request.getUserId())) {
            throw new IllegalArgumentException("seckill order request not found");
        }
        return buildTrace(request);
    }

    public SeckillOrderRequestProcessResult processPendingOrderRequests(Integer limit) {
        int normalizedLimit = normalizeProcessLimit(limit);
        LocalDateTime now = LocalDateTime.now();
        List<TradeLocalMessageEntity> messages = seckillRepository.queryPendingSeckillOrderMessages(now, normalizedLimit);

        SeckillOrderRequestProcessResult result = new SeckillOrderRequestProcessResult();
        result.setScannedCount(messages.size());
        for (TradeLocalMessageEntity message : messages) {
            if (!seckillRepository.markLocalMessageProcessing(message.getId())) {
                continue;
            }
            processSingleOrderRequestMessage(message, result);
        }
        return result;
    }

    public SeckillOrderRequestRecoveryResult recoverOrderRequests(Integer limit) {
        int normalizedLimit = normalizeRecoveryLimit(limit);
        LocalDateTime now = LocalDateTime.now();
        SeckillOrderRequestRecoveryResult result = new SeckillOrderRequestRecoveryResult();

        List<TradeLocalMessageEntity> stuckMessages = seckillRepository.queryProcessingSeckillOrderMessages(
                now.minusSeconds(DEFAULT_PROCESSING_STUCK_SECONDS), normalizedLimit);
        result.setScannedMessageCount(stuckMessages.size());
        for (TradeLocalMessageEntity message : stuckMessages) {
            if (seckillRepository.recoverProcessingLocalMessage(message.getId(), now)) {
                result.setRecoveredMessageCount(result.getRecoveredMessageCount() + 1);
            }
        }

        List<SeckillOrderRequestEntity> timeoutRequests = seckillRepository.queryTimeoutInitOrProcessingRequests(
                now.minusSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS), normalizedLimit);
        for (SeckillOrderRequestEntity request : timeoutRequests) {
            if (seckillRepository.cancelTimeoutSeckillOrderRequest(request.getRequestNo(), "seckill order request timeout")) {
                seckillStockRepository.releaseActivityStock(request.getActivityId(), request.getUserId());
                result.setCanceledRequestCount(result.getCanceledRequestCount() + 1);
                result.setReleasedStockCount(result.getReleasedStockCount() + 1);
            }
        }
        return result;
    }

    public SeckillStockReconcileResult reconcileActivityStock(Long activityId) {
        if (activityId == null) {
            throw new IllegalArgumentException("activityId required");
        }
        LocalDateTime now = LocalDateTime.now();
        SeckillActivityEntity activity = seckillRepository.queryActivityById(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("seckill activity not found");
        }
        Integer redisStockBefore = seckillStockRepository.queryActivityStock(activityId);
        seckillStockRepository.refreshActivityStock(activity, now, activity.getStock());
        Integer redisStockAfter = seckillStockRepository.queryActivityStock(activityId);

        SeckillStockReconcileResult result = new SeckillStockReconcileResult();
        result.setActivityId(activityId);
        result.setDbStock(activity.getStock());
        result.setRedisStockBefore(redisStockBefore);
        result.setRedisStockAfter(redisStockAfter);
        result.setWaitPayCount(seckillRepository.querySeckillOrderCount(activityId, OrderStatusConstants.WAIT_PAY));
        result.setPaidCount(seckillRepository.querySeckillOrderCount(activityId, OrderStatusConstants.PAID));
        result.setRefreshed(redisStockBefore == null || !redisStockBefore.equals(redisStockAfter));
        return result;
    }

    private void checkCommand(SeckillOrderCommand command) {
        if (command == null || command.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (command.getActivityId() == null) {
            throw new IllegalArgumentException("activityId required");
        }
        if (command.getQuantity() == null || command.getQuantity() != 1) {
            throw new IllegalArgumentException("seckill quantity must be 1");
        }
    }

    private SeckillActivityEntity queryAndCheckActivity(SeckillOrderCommand command, LocalDateTime now) {
        SeckillActivityEntity activity = seckillRepository.queryActivityById(command.getActivityId());
        if (activity == null) {
            throw new IllegalArgumentException("seckill activity not found");
        }
        if (activity.getActivityStatus() == null || activity.getActivityStatus() != 1) {
            throw new IllegalArgumentException("seckill activity disabled");
        }
        if (activity.getValidStartTime().isAfter(now)) {
            throw new IllegalArgumentException("seckill activity not start");
        }
        if (!activity.getValidEndTime().isAfter(now)) {
            throw new IllegalArgumentException("seckill activity ended");
        }
        if (activity.getStock() == null || activity.getStock() <= 0) {
            throw new IllegalArgumentException("seckill stock not enough");
        }
        return activity;
    }

    private void checkUserTakeLimit(SeckillOrderCommand command, SeckillActivityEntity activity) {
        int takeCount = seckillRepository.queryUserTakeCount(activity.getId(), command.getUserId());
        if (takeCount >= activity.getUserTakeLimit()) {
            throw new IllegalArgumentException("seckill user take limit");
        }
    }

    private SeckillStockOccupyResult occupyActivityStock(SeckillActivityEntity activity, Long userId, LocalDateTime now) {
        SeckillStockOccupyResult result = seckillStockRepository.occupyActivityStock(activity, userId, now);
        if (Boolean.TRUE.equals(result.getSuccess())) {
            return result;
        }
        if (SeckillStockOccupyResult.ACTIVITY_NOT_PREHEATED.equals(result.getRejectCode())) {
            seckillStockRepository.preheatActivityStock(activity, now);
            result = seckillStockRepository.occupyActivityStock(activity, userId, now);
        }
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            throw new IllegalArgumentException(result.getRejectMessage());
        }
        return result;
    }

    private void processSingleOrderRequestMessage(TradeLocalMessageEntity message,
                                                  SeckillOrderRequestProcessResult processResult) {
        SeckillOrderRequestEntity request = seckillRepository.querySeckillOrderRequest(message.getBizId());
        if (request == null) {
            seckillRepository.markLocalMessageFailed(message.getId(), "seckill order request not found");
            processResult.setFailedCount(processResult.getFailedCount() + 1);
            return;
        }
        if (SeckillRequestStatusConstants.SUCCESS.equals(request.getRequestStatus())) {
            seckillRepository.markLocalMessageSuccess(message.getId());
            processResult.setSuccessCount(processResult.getSuccessCount() + 1);
            return;
        }
        if (!seckillRepository.markSeckillOrderRequestProcessing(request.getRequestNo())) {
            seckillRepository.markLocalMessageRetry(message.getId(), "request status can not process", LocalDateTime.now().plusSeconds(30));
            processResult.setRetryCount(processResult.getRetryCount() + 1);
            return;
        }

        try {
            SeckillOrderResult orderResult = createActualOrderFromRequest(request);
            seckillRepository.markSeckillOrderRequestSuccess(request.getRequestNo(), orderResult);
            seckillRepository.markLocalMessageSuccess(message.getId());
            tradeEventPublisher.publish(TradeMqTopics.TRADE_ORDER_TOPIC,
                    TradeMqTopics.ORDER_CREATED,
                    String.valueOf(orderResult.getOrderId()),
                    orderResult);
            scheduleOrderTimeoutClose(orderResult, request.getUserId());
            processResult.setSuccessCount(processResult.getSuccessCount() + 1);
        } catch (Exception e) {
            String failReason = rootMessage(e);
            if (message.getRetryCount() + 1 >= message.getMaxRetryCount()) {
                seckillRepository.markSeckillOrderRequestFailed(request.getRequestNo(), failReason);
                seckillRepository.markLocalMessageFailed(message.getId(), failReason);
                seckillStockRepository.releaseActivityStock(request.getActivityId(), request.getUserId());
                processResult.setFailedCount(processResult.getFailedCount() + 1);
                return;
            }
            seckillRepository.markSeckillOrderRequestFailed(request.getRequestNo(), failReason);
            seckillRepository.markLocalMessageRetry(message.getId(), failReason, LocalDateTime.now().plusSeconds(30));
            processResult.setRetryCount(processResult.getRetryCount() + 1);
        }
    }

    private SeckillOrderResult createActualOrderFromRequest(SeckillOrderRequestEntity request) {
        LocalDateTime now = LocalDateTime.now();
        SeckillActivityEntity activity = seckillRepository.queryActivityById(request.getActivityId());
        if (activity == null) {
            throw new IllegalArgumentException("seckill activity not found");
        }
        if (activity.getActivityStatus() == null || activity.getActivityStatus() != 1) {
            throw new IllegalArgumentException("seckill activity disabled");
        }
        PackageTradeSnapshot snapshot = queryAndCheckSnapshot(activity);

        SeckillOrderCommand command = new SeckillOrderCommand();
        command.setUserId(request.getUserId());
        command.setActivityId(request.getActivityId());
        command.setQuantity(request.getQuantity());

        SeckillOrderAggregate aggregate = buildAggregate(command, activity, snapshot);
        return seckillRepository.saveSeckillOrder(aggregate, now);
    }

    private SeckillOrderTraceEntity buildTrace(SeckillOrderRequestEntity request) {
        SeckillActivityEntity activity = seckillRepository.queryActivityById(request.getActivityId());
        PackageTradeSnapshot snapshot = activity == null ? null : businessPackagePort.queryTradeSnapshot(activity.getPackageId());
        DiningOrderEntity order = request.getOrderId() == null
                ? null
                : orderRepository.findOrderByIdAndUserId(request.getOrderId(), request.getUserId());
        List<DiningOrderItemEntity> orderItems = order == null
                ? java.util.Collections.emptyList()
                : orderRepository.listOrderItems(order.getId());

        SeckillOrderTraceEntity trace = new SeckillOrderTraceEntity();
        trace.setRequest(request);
        trace.setOrder(order);
        trace.setOrderItems(orderItems);
        trace.setActivity(activity);
        trace.setPackageSnapshot(snapshot);
        trace.setDbStock(activity == null ? null : activity.getStock());
        trace.setRedisStock(seckillStockRepository.queryActivityStock(request.getActivityId()));
        trace.setWaitPayCount(seckillRepository.querySeckillOrderCount(request.getActivityId(), OrderStatusConstants.WAIT_PAY));
        trace.setPaidCount(seckillRepository.querySeckillOrderCount(request.getActivityId(), OrderStatusConstants.PAID));
        trace.setOrderCreated(order != null);
        trace.setCurrentStage(resolveCurrentStage(request, order));
        return trace;
    }

    private String resolveCurrentStage(SeckillOrderRequestEntity request, DiningOrderEntity order) {
        if (SeckillRequestStatusConstants.INIT.equals(request.getRequestStatus())
                || SeckillRequestStatusConstants.PROCESSING.equals(request.getRequestStatus())) {
            return "ORDER_CREATING";
        }
        if (SeckillRequestStatusConstants.FAILED.equals(request.getRequestStatus())) {
            return "REQUEST_FAILED";
        }
        if (order == null) {
            return "ORDER_UNKNOWN";
        }
        if (OrderStatusConstants.WAIT_PAY.equals(order.getOrderStatus())) {
            return "WAIT_PAY";
        }
        if (OrderStatusConstants.PAID.equals(order.getOrderStatus())) {
            return "PAID";
        }
        if (OrderStatusConstants.USED.equals(order.getOrderStatus())) {
            return "USED";
        }
        if (OrderStatusConstants.CANCELED.equals(order.getOrderStatus())) {
            return "CANCELED";
        }
        if (OrderStatusConstants.REFUNDED.equals(order.getOrderStatus())) {
            return "REFUNDED";
        }
        return order.getOrderStatus();
    }

    private void releaseOccupiedStock(boolean stockOccupied, SeckillActivityEntity activity, SeckillOrderCommand command) {
        if (!stockOccupied || activity == null || command == null || command.getUserId() == null) {
            return;
        }
        seckillStockRepository.releaseActivityStock(activity.getId(), command.getUserId());
    }

    private PackageTradeSnapshot queryAndCheckSnapshot(SeckillActivityEntity activity) {
        PackageTradeSnapshot snapshot = businessPackagePort.queryTradeSnapshot(activity.getPackageId());
        if (snapshot == null) {
            throw new IllegalArgumentException("package not found");
        }
        if (snapshot.getPackageStatus() == null || snapshot.getPackageStatus() != 1) {
            throw new IllegalArgumentException("package disabled");
        }
        return snapshot;
    }

    private SeckillOrderAggregate buildAggregate(SeckillOrderCommand command,
                                                 SeckillActivityEntity activity,
                                                 PackageTradeSnapshot snapshot) {
        OrderPricingResult pricingResult = new OrderPricingResult();
        pricingResult.setTotalAmount(snapshot.getPrice() * command.getQuantity());
        pricingResult.setPayAmount(activity.getSeckillPrice() * command.getQuantity());

        CreateOrderCommand createOrderCommand = new CreateOrderCommand();
        createOrderCommand.setUserId(command.getUserId());
        createOrderCommand.setPackageId(activity.getPackageId());
        createOrderCommand.setQuantity(command.getQuantity());

        DiningOrderEntity order = orderFactory.createOrder(TradeTypeConstants.SECKILL, createOrderCommand, snapshot, pricingResult);
        DiningOrderItemEntity orderItem = orderFactory.createOrderItem(order, snapshot, command.getQuantity());

        SeckillOrderEntity seckillOrder = new SeckillOrderEntity();
        seckillOrder.setUserId(command.getUserId());
        seckillOrder.setActivityId(activity.getId());
        seckillOrder.setPackageId(activity.getPackageId());
        seckillOrder.setOrderStatus(OrderStatusConstants.WAIT_PAY);
        seckillOrder.setCreateTime(order.getCreateTime());
        seckillOrder.setUpdateTime(order.getUpdateTime());

        SeckillOrderAggregate aggregate = new SeckillOrderAggregate();
        aggregate.setActivity(activity);
        aggregate.setOrder(order);
        aggregate.setOrderItem(orderItem);
        aggregate.setSeckillOrder(seckillOrder);
        return aggregate;
    }

    private SeckillOrderRequestEntity buildOrderRequest(SeckillOrderCommand command,
                                                        SeckillActivityEntity activity,
                                                        String requestNo,
                                                        LocalDateTime now) {
        SeckillOrderRequestEntity request = new SeckillOrderRequestEntity();
        request.setRequestNo(requestNo);
        request.setUserId(command.getUserId());
        request.setActivityId(activity.getId());
        request.setPackageId(activity.getPackageId());
        request.setQuantity(command.getQuantity());
        request.setRequestStatus(SeckillRequestStatusConstants.INIT);
        request.setCreateTime(now);
        request.setUpdateTime(now);
        return request;
    }

    private TradeLocalMessageEntity buildLocalMessage(SeckillOrderRequestEntity request, LocalDateTime now) {
        TradeLocalMessageEntity message = new TradeLocalMessageEntity();
        message.setMessageId("MSG" + UUID.randomUUID().toString().replace("-", ""));
        message.setMessageType(MESSAGE_TYPE_SECKILL_ORDER_CREATE);
        message.setBizType(BIZ_TYPE_SECKILL_ORDER_REQUEST);
        message.setBizId(request.getRequestNo());
        message.setMessageStatus(LocalMessageStatusConstants.INIT);
        message.setRetryCount(0);
        message.setMaxRetryCount(MAX_MESSAGE_RETRY_COUNT);
        message.setNextRetryTime(now);
        message.setContent(buildMessageContent(request));
        message.setCreateTime(now);
        message.setUpdateTime(now);
        return message;
    }

    private SeckillOrderRequestResult toRequestResult(SeckillOrderRequestEntity request) {
        SeckillOrderRequestResult result = new SeckillOrderRequestResult();
        result.setRequestNo(request.getRequestNo());
        result.setUserId(request.getUserId());
        result.setActivityId(request.getActivityId());
        result.setPackageId(request.getPackageId());
        result.setQuantity(request.getQuantity());
        result.setOrderId(request.getOrderId());
        result.setOrderNo(request.getOrderNo());
        result.setRequestStatus(request.getRequestStatus());
        result.setFailReason(request.getFailReason());
        return result;
    }

    private String buildMessageContent(SeckillOrderRequestEntity request) {
        return "{\"requestNo\":\"" + request.getRequestNo()
                + "\",\"userId\":" + request.getUserId()
                + ",\"activityId\":" + request.getActivityId()
                + ",\"packageId\":" + request.getPackageId()
                + ",\"quantity\":" + request.getQuantity()
                + "}";
    }

    private void scheduleOrderTimeoutClose(SeckillOrderResult orderResult, Long userId) {
        OrderTimeoutCloseMessage message = new OrderTimeoutCloseMessage();
        message.setOrderId(orderResult.getOrderId());
        message.setOrderNo(orderResult.getOrderNo());
        message.setUserId(userId);
        message.setTradeType(TradeTypeConstants.SECKILL);
        tradeEventPublisher.publishDelay(TradeMqTopics.TRADE_ORDER_TOPIC,
                TradeMqTopics.ORDER_CANCEL_TIMEOUT,
                "timeout-close:" + orderResult.getOrderId(),
                message);
    }

    private String generateRequestNo(Long userId) {
        return "SK" + System.currentTimeMillis() + userId;
    }

    private int normalizeProcessLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PROCESS_LIMIT;
        }
        return Math.min(limit, MAX_PROCESS_LIMIT);
    }

    private int normalizeRecoveryLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RECOVERY_LIMIT;
        }
        return Math.min(limit, MAX_RECOVERY_LIMIT);
    }

    private String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? e.getMessage() : current.getMessage();
    }

    private SeckillActivityView fillRedisStock(SeckillActivityView view) {
        Integer redisStock = seckillStockRepository.queryActivityStock(view.getActivityId());
        if (redisStock == null) {
            return view;
        }
        view.setStock(redisStock);
        view.setCanBuy(Boolean.TRUE.equals(view.getCanBuy()) && redisStock > 0);
        return view;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_ACTIVITY_LIMIT;
        }
        return Math.min(limit, MAX_ACTIVITY_LIMIT);
    }
}
