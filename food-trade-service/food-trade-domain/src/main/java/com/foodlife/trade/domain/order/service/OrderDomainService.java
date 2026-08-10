package com.foodlife.trade.domain.order.service;

import com.foodlife.trade.domain.order.check.OrderCreateCheckChain;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
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
import com.foodlife.trade.domain.order.pricing.OrderPricingService;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityView;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderCommand;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestProcessResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestRecoveryResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockReconcileResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockPreheatResult;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import com.foodlife.trade.domain.order.seckill.service.SeckillOrderService;
import com.foodlife.trade.domain.order.service.refund.OrderRefundService;
import com.foodlife.trade.domain.order.service.settlement.OrderPaySettlementService;
import com.foodlife.trade.domain.order.service.use.OrderUseService;
import org.springframework.stereotype.Service;

@Service
public class OrderDomainService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

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
                              ISeckillStockRepository seckillStockRepository) {
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
    }

    public CreateOrderResult createNormalOrder(CreateOrderCommand command) {
        try {
            OrderCreateContext context = buildCreateContext(TradeTypeConstants.NORMAL, command);
            orderCreateCheckChain.checkNormalOrder(context);

            PackageTradeSnapshot snapshot = context.getPackageSnapshot();
            OrderPricingResult pricingResult = orderPricingService.calculate(context);

            DiningOrderEntity order = orderFactory.createOrder(context.getTradeType(), command, snapshot, pricingResult);
            DiningOrderEntity savedOrder = orderRepository.saveOrder(order);

            DiningOrderItemEntity orderItem = orderFactory.createOrderItem(savedOrder, snapshot, command.getQuantity());
            orderRepository.saveOrderItem(orderItem);

            return buildCreateOrderResult(savedOrder);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
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
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        int normalizedPageSize = normalizePageSize(pageSize);
        java.util.List<DiningOrderEntity> orders = orderRepository.listUserOrders(userId, lastId, normalizedPageSize + 1);
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
        return buildCancelOrderResult(order);
    }

    public OrderPaySettlementEntity payOrderMock(OrderPaySuccessEntity paySuccessEntity) {
        return orderPaySettlementService.settlementOrderPaySuccess(paySuccessEntity);
    }

    public OrderRefundBehaviorEntity refundOrderMock(OrderRefundCommandEntity command) {
        return orderRefundService.refundOrder(command);
    }

    public OrderUseResult useOrderMock(OrderUseCommandEntity command) {
        return orderUseService.useOrder(command);
    }

    public GroupBuyLockResult createGroupBuyOrder(GroupBuyLockOrderCommand command) {
        return groupBuyLockOrderService.lockOrder(command);
    }

    public java.util.List<SeckillActivityView> querySeckillActivities(Long packageId, Integer limit) {
        return seckillOrderService.queryAvailableActivities(packageId, limit);
    }

    public SeckillStockPreheatResult preheatSeckillActivityStock(Long activityId) {
        return seckillOrderService.preheatActivityStock(activityId);
    }

    public SeckillOrderResult createSeckillOrder(SeckillOrderCommand command) {
        return seckillOrderService.createSeckillOrder(command);
    }

    public SeckillOrderRequestResult createSeckillOrderRequest(SeckillOrderCommand command) {
        return seckillOrderService.createSeckillOrderRequest(command);
    }

    public SeckillOrderRequestResult querySeckillOrderRequest(String requestNo, Long userId) {
        return seckillOrderService.querySeckillOrderRequest(requestNo, userId);
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
        summary.setPayAmount(order.getPayAmount());
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
        result.setPayAmount(savedOrder.getPayAmount());
        result.setOrderStatus(savedOrder.getOrderStatus());
        return result;
    }

    private CancelOrderResult buildCancelOrderResult(DiningOrderEntity order) {
        CancelOrderResult result = new CancelOrderResult();
        result.setOrderId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setOrderStatus(OrderStatusConstants.CANCELED);
        return result;
    }
}
