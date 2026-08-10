package com.foodlife.trade.domain.order.seckill.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityView;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderAggregate;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderCommand;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockOccupyResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockPreheatResult;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SeckillOrderService {

    private static final int DEFAULT_ACTIVITY_LIMIT = 20;
    private static final int MAX_ACTIVITY_LIMIT = 50;

    private final ISeckillRepository seckillRepository;
    private final ISeckillStockRepository seckillStockRepository;
    private final IBusinessPackagePort businessPackagePort;
    private final OrderFactory orderFactory;

    public SeckillOrderService(ISeckillRepository seckillRepository,
                               ISeckillStockRepository seckillStockRepository,
                               IBusinessPackagePort businessPackagePort,
                               OrderFactory orderFactory) {
        this.seckillRepository = seckillRepository;
        this.seckillStockRepository = seckillStockRepository;
        this.businessPackagePort = businessPackagePort;
        this.orderFactory = orderFactory;
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
        } catch (Exception e) {
            releaseOccupiedStock(stockOccupied, activity, command);
            throw new IllegalStateException("seckill order create failed", e);
        }
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
