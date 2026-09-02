package com.foodlife.trade.domain.order.service;

import com.foodlife.trade.domain.order.check.OrderCreateCheckChain;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.coupon.model.UserCouponEntity;
import com.foodlife.trade.domain.order.coupon.service.CouponService;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.event.TradeMqTopics;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockOrderCommand;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockResult;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.groupbuy.service.GroupBuyLockOrderService;
import com.foodlife.trade.domain.order.model.CancelOrderResult;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderDetailEntity;
import com.foodlife.trade.domain.order.model.OrderListResult;
import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import com.foodlife.trade.domain.order.model.OrderPaySuccessEntity;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSummaryEntity;
import com.foodlife.trade.domain.order.model.OrderUseCommandEntity;
import com.foodlife.trade.domain.order.model.OrderUseResult;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.normal.service.NormalPackageStockMessageService;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.pricing.OrderPricingService;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityView;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderCommand;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestProcessResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestRecoveryResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderTraceEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockReconcileResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockPreheatResult;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import com.foodlife.trade.domain.order.seckill.service.SeckillOrderService;
import com.foodlife.trade.domain.order.service.refund.OrderRefundService;
import com.foodlife.trade.domain.order.service.settlement.OrderPaySettlementService;
import com.foodlife.trade.domain.order.service.use.OrderUseService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class OrderDomainService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> SUPPORT_TRADE_TYPES = new HashSet<String>(Arrays.asList(
            TradeTypeConstants.NORMAL,
            TradeTypeConstants.GROUP_BUY,
            TradeTypeConstants.SECKILL
    ));
    private static final Set<String> SUPPORT_ORDER_STATUSES = new HashSet<String>(Arrays.asList(
            OrderStatusConstants.WAIT_PAY,
            OrderStatusConstants.PAID,
            OrderStatusConstants.USED,
            OrderStatusConstants.CANCELED,
            OrderStatusConstants.REFUNDED
    ));

    private final IOrderRepository orderRepository;
    private final OrderCreateCheckChain orderCreateCheckChain;
    private final OrderPricingService orderPricingService;
    private final OrderFactory orderFactory;
    private final OrderPaySettlementService orderPaySettlementService;
    private final OrderRefundService orderRefundService;
    private final OrderUseService orderUseService;
    private final GroupBuyLockOrderService groupBuyLockOrderService;
    private final IGroupBuyRepository groupBuyRepository;
    private final SeckillOrderService seckillOrderService;
    private final ISeckillRepository seckillRepository;
    private final ISeckillStockRepository seckillStockRepository;
    private final IBusinessPackagePort businessPackagePort;
    private final NormalPackageStockMessageService normalPackageStockMessageService;
    private final CouponService couponService;
    private final ITradeEventPublisher tradeEventPublisher;

    public OrderDomainService(IOrderRepository orderRepository,
                              OrderCreateCheckChain orderCreateCheckChain,
                              OrderPricingService orderPricingService,
                              OrderFactory orderFactory,
                              OrderPaySettlementService orderPaySettlementService,
                              OrderRefundService orderRefundService,
                              OrderUseService orderUseService,
                              GroupBuyLockOrderService groupBuyLockOrderService,
                              IGroupBuyRepository groupBuyRepository,
                              SeckillOrderService seckillOrderService,
                              ISeckillRepository seckillRepository,
                              ISeckillStockRepository seckillStockRepository,
                              IBusinessPackagePort businessPackagePort,
                              NormalPackageStockMessageService normalPackageStockMessageService,
                              CouponService couponService,
                              ITradeEventPublisher tradeEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderCreateCheckChain = orderCreateCheckChain;
        this.orderPricingService = orderPricingService;
        this.orderFactory = orderFactory;
        this.orderPaySettlementService = orderPaySettlementService;
        this.orderRefundService = orderRefundService;
        this.orderUseService = orderUseService;
        this.groupBuyLockOrderService = groupBuyLockOrderService;
        this.groupBuyRepository = groupBuyRepository;
        this.seckillOrderService = seckillOrderService;
        this.seckillRepository = seckillRepository;
        this.seckillStockRepository = seckillStockRepository;
        this.businessPackagePort = businessPackagePort;
        this.normalPackageStockMessageService = normalPackageStockMessageService;
        this.couponService = couponService;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    public CreateOrderResult createNormalOrder(CreateOrderCommand command) {
        boolean packageStockOccupied = false;
        Long usedCouponId = null;
        Long savedOrderId = null;
        try {
            OrderCreateContext context = buildCreateContext(TradeTypeConstants.NORMAL, command);
            orderCreateCheckChain.checkNormalOrder(context);
            Long totalAmount = context.getPackageSnapshot().getPrice() * command.getQuantity();
            UserCouponEntity userCoupon = couponService.validateCouponForOrder(command.getUserCouponId(), command.getUserId(), totalAmount, context.getPackageSnapshot());
            context.setUserCoupon(userCoupon);

            PackageTradeSnapshot snapshot = context.getPackageSnapshot();
            OrderPricingResult pricingResult = orderPricingService.calculate(context);
            businessPackagePort.occupyPackageStock(command.getPackageId(), command.getQuantity());
            packageStockOccupied = true;

            DiningOrderEntity order = orderFactory.createOrder(context.getTradeType(), command, snapshot, pricingResult);
            DiningOrderEntity savedOrder = orderRepository.saveOrder(order);
            savedOrderId = savedOrder.getId();
            usedCouponId = savedOrder.getUserCouponId();
            couponService.markCouponUsed(usedCouponId, command.getUserId(), savedOrder.getId());

            DiningOrderItemEntity orderItem = orderFactory.createOrderItem(savedOrder, snapshot, command.getQuantity());
            orderRepository.saveOrderItem(orderItem);
            tradeEventPublisher.publish(TradeMqTopics.TRADE_ORDER_TOPIC,
                    TradeMqTopics.ORDER_CREATED,
                    String.valueOf(savedOrder.getId()),
                    savedOrder);

            return buildCreateOrderResult(savedOrder);
        } catch (IllegalArgumentException e) {
            releaseOccupiedPackageStock(packageStockOccupied, command);
            couponService.releaseCoupon(usedCouponId, command == null ? null : command.getUserId(), savedOrderId);
            throw e;
        } catch (IllegalStateException e) {
            releaseOccupiedPackageStock(packageStockOccupied, command);
            couponService.releaseCoupon(usedCouponId, command == null ? null : command.getUserId(), savedOrderId);
            throw e;
        } catch (Exception e) {
            releaseOccupiedPackageStock(packageStockOccupied, command);
            couponService.releaseCoupon(usedCouponId, command == null ? null : command.getUserId(), savedOrderId);
            throw new IllegalStateException("normal order create failed", e);
        }
    }

    public OrderDetailEntity queryOrderDetail(Long orderId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId required");
        }
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        OrderDetailEntity detail = new OrderDetailEntity();
        detail.setOrder(order);
        detail.setItems(orderRepository.listOrderItems(orderId));
        return detail;
    }

    public OrderListResult queryUserOrderList(Long userId, Long lastId, Integer pageSize) {
        return queryUserOrderList(userId, lastId, pageSize, null, null);
    }

    public OrderListResult queryUserOrderList(Long userId, Long lastId, Integer pageSize,
                                              String tradeType, String orderStatus) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedTradeType = normalizeTradeType(tradeType);
        String normalizedOrderStatus = normalizeOrderStatus(orderStatus);
        java.util.List<DiningOrderEntity> orders = orderRepository.listUserOrders(userId, lastId, normalizedPageSize + 1,
                normalizedTradeType, normalizedOrderStatus);
        boolean hasMore = orders.size() > normalizedPageSize;
        if (hasMore) {
            orders = orders.subList(0, normalizedPageSize);
        }

        java.util.List<OrderSummaryEntity> summaries = orders.stream()
                .map(this::toOrderSummary)
                .collect(java.util.stream.Collectors.toList());

        OrderListResult result = new OrderListResult();
        result.setOrders(summaries);
        result.setHasMore(hasMore);
        result.setLastId(summaries.isEmpty() ? null : summaries.get(summaries.size() - 1).getOrderId());
        result.setTradeType(normalizedTradeType);
        result.setOrderStatus(normalizedOrderStatus);
        return result;
    }

    public CancelOrderResult cancelOrder(Long orderId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId required");
        }
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(orderId, userId);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (!OrderStatusConstants.WAIT_PAY.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order status can not cancel");
        }

        if (TradeTypeConstants.GROUP_BUY.equals(order.getTradeType())) {
            groupBuyRepository.cancelUnpaidGroupBuyOrder(order);
            return buildCancelOrderResult(order);
        }

        if (TradeTypeConstants.SECKILL.equals(order.getTradeType())) {
            seckillRepository.cancelUnpaidSeckillOrder(order);
            seckillStockRepository.releaseActivityStock(seckillRepository.querySeckillActivityId(order), userId);
            return buildCancelOrderResult(order);
        }

        boolean success = orderRepository.updateOrderStatus(orderId, OrderStatusConstants.WAIT_PAY, OrderStatusConstants.CANCELED);
        if (!success) {
            throw new IllegalArgumentException("order status can not cancel");
        }
        couponService.releaseCoupon(order.getUserCouponId(), order.getUserId(), order.getId());
        normalPackageStockMessageService.releaseStock(order);
        return buildCancelOrderResult(order);
    }

    public OrderPaySettlementEntity payOrderMock(OrderPaySuccessEntity paySuccessEntity) {
        return orderPaySettlementService.settlementOrderPaySuccess(paySuccessEntity);
    }

    public OrderRefundBehaviorEntity refundOrderMock(OrderRefundCommandEntity command) {
        OrderRefundBehaviorEntity refund = orderRefundService.refundOrder(command);
        tradeEventPublisher.publish(TradeMqTopics.TRADE_ORDER_TOPIC,
                TradeMqTopics.ORDER_REFUND_REQUESTED,
                String.valueOf(refund.getOrderId()),
                refund);
        tradeEventPublisher.publish(TradeMqTopics.PAYMENT_TOPIC,
                TradeMqTopics.PAYMENT_REFUNDED,
                String.valueOf(refund.getOrderId()),
                refund);
        return refund;
    }

    public OrderUseResult useOrderMock(OrderUseCommandEntity command) {
        OrderUseResult result = orderUseService.useOrder(command);
        tradeEventPublisher.publish(TradeMqTopics.TRADE_ORDER_TOPIC,
                TradeMqTopics.ORDER_USED,
                String.valueOf(result.getOrderId()),
                result);
        return result;
    }

    public GroupBuyLockResult createGroupBuyOrder(GroupBuyLockOrderCommand command) {
        GroupBuyLockResult result = groupBuyLockOrderService.lockOrder(command);
        tradeEventPublisher.publish(TradeMqTopics.TRADE_ORDER_TOPIC,
                TradeMqTopics.ORDER_CREATED,
                String.valueOf(result.getOrderId()),
                result);
        return result;
    }

    public java.util.List<SeckillActivityView> querySeckillActivities(Long packageId, Integer limit) {
        return seckillOrderService.queryAvailableActivities(packageId, limit);
    }

    public SeckillStockPreheatResult preheatSeckillActivityStock(Long activityId) {
        return seckillOrderService.preheatActivityStock(activityId);
    }

    public SeckillOrderResult createSeckillOrder(SeckillOrderCommand command) {
        SeckillOrderResult result = seckillOrderService.createSeckillOrder(command);
        tradeEventPublisher.publish(TradeMqTopics.TRADE_ORDER_TOPIC,
                TradeMqTopics.ORDER_CREATED,
                String.valueOf(result.getOrderId()),
                result);
        return result;
    }

    public SeckillOrderRequestResult createSeckillOrderRequest(SeckillOrderCommand command) {
        return seckillOrderService.createSeckillOrderRequest(command);
    }

    public SeckillOrderRequestResult querySeckillOrderRequest(String requestNo, Long userId) {
        return seckillOrderService.querySeckillOrderRequest(requestNo, userId);
    }

    public SeckillOrderTraceEntity querySeckillOrderTraceByRequestNo(String requestNo, Long userId) {
        return seckillOrderService.queryOrderTraceByRequestNo(requestNo, userId);
    }

    public SeckillOrderTraceEntity querySeckillOrderTraceByOrderId(Long orderId, Long userId) {
        return seckillOrderService.queryOrderTraceByOrderId(orderId, userId);
    }

    public SeckillOrderRequestProcessResult processPendingSeckillOrderRequests(Integer limit) {
        return seckillOrderService.processPendingOrderRequests(limit);
    }

    public SeckillOrderRequestRecoveryResult recoverSeckillOrderRequests(Integer limit) {
        return seckillOrderService.recoverOrderRequests(limit);
    }

    public SeckillStockReconcileResult reconcileSeckillActivityStock(Long activityId) {
        return seckillOrderService.reconcileActivityStock(activityId);
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeTradeType(String tradeType) {
        if (tradeType == null || tradeType.trim().isEmpty()) {
            return null;
        }
        String value = tradeType.trim().toUpperCase();
        if (!SUPPORT_TRADE_TYPES.contains(value)) {
            throw new IllegalArgumentException("tradeType not supported");
        }
        return value;
    }

    private String normalizeOrderStatus(String orderStatus) {
        if (orderStatus == null || orderStatus.trim().isEmpty()) {
            return null;
        }
        String value = orderStatus.trim().toUpperCase();
        if (!SUPPORT_ORDER_STATUSES.contains(value)) {
            throw new IllegalArgumentException("orderStatus not supported");
        }
        return value;
    }

    private OrderSummaryEntity toOrderSummary(DiningOrderEntity order) {
        DiningOrderItemEntity firstItem = orderRepository.listOrderItems(order.getId())
                .stream()
                .findFirst()
                .orElse(null);

        OrderSummaryEntity summary = new OrderSummaryEntity();
        summary.setOrderId(order.getId());
        summary.setOrderNo(order.getOrderNo());
        summary.setUserId(order.getUserId());
        summary.setShopId(order.getShopId());
        summary.setPackageId(order.getPackageId());
        summary.setQuantity(order.getQuantity());
        summary.setTotalAmount(order.getTotalAmount());
        summary.setDiscountAmount(order.getDiscountAmount());
        summary.setPayAmount(order.getPayAmount());
        summary.setUserCouponId(order.getUserCouponId());
        summary.setTradeType(order.getTradeType());
        summary.setOrderStatus(order.getOrderStatus());
        summary.setUseTime(order.getUseTime());
        summary.setCreateTime(order.getCreateTime());
        if (firstItem != null) {
            summary.setShopNameSnapshot(firstItem.getShopNameSnapshot());
            summary.setPackageNameSnapshot(firstItem.getPackageNameSnapshot());
            summary.setCoverImageSnapshot(firstItem.getCoverImageSnapshot());
        }
        return summary;
    }

    private OrderCreateContext buildCreateContext(String tradeType, CreateOrderCommand command) {
        OrderCreateContext context = new OrderCreateContext();
        context.setTradeType(tradeType);
        context.setCommand(command);
        return context;
    }

    private CreateOrderResult buildCreateOrderResult(DiningOrderEntity savedOrder) {
        CreateOrderResult result = new CreateOrderResult();
        result.setOrderId(savedOrder.getId());
        result.setOrderNo(savedOrder.getOrderNo());
        result.setTotalAmount(savedOrder.getTotalAmount());
        result.setDiscountAmount(savedOrder.getDiscountAmount());
        result.setPayAmount(savedOrder.getPayAmount());
        result.setUserCouponId(savedOrder.getUserCouponId());
        result.setOrderStatus(savedOrder.getOrderStatus());
        return result;
    }

    private void releaseOccupiedPackageStock(boolean stockOccupied, CreateOrderCommand command) {
        if (!stockOccupied || command == null || command.getPackageId() == null || command.getQuantity() == null) {
            return;
        }
        businessPackagePort.releasePackageStock(command.getPackageId(), command.getQuantity());
    }

    private CancelOrderResult buildCancelOrderResult(DiningOrderEntity order) {
        CancelOrderResult result = new CancelOrderResult();
        result.setOrderId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setOrderStatus(OrderStatusConstants.CANCELED);
        return result;
    }
}
