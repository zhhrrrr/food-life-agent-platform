package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.message.constant.LocalMessageStatusConstants;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.seckill.constant.SeckillRequestStatusConstants;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityView;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderAggregate;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderResult;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.infrastructure.dao.IDiningOrderItemMapper;
import com.foodlife.trade.infrastructure.dao.IDiningOrderMapper;
import com.foodlife.trade.infrastructure.dao.ISeckillActivityMapper;
import com.foodlife.trade.infrastructure.dao.ISeckillOrderMapper;
import com.foodlife.trade.infrastructure.dao.ISeckillOrderRequestMapper;
import com.foodlife.trade.infrastructure.dao.ITradeLocalMessageMapper;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderItemPO;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderPO;
import com.foodlife.trade.infrastructure.dao.po.SeckillActivityPO;
import com.foodlife.trade.infrastructure.dao.po.SeckillOrderPO;
import com.foodlife.trade.infrastructure.dao.po.SeckillOrderRequestPO;
import com.foodlife.trade.infrastructure.dao.po.TradeLocalMessagePO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class SeckillRepository implements ISeckillRepository {

    private final ISeckillActivityMapper seckillActivityMapper;
    private final ISeckillOrderMapper seckillOrderMapper;
    private final ISeckillOrderRequestMapper seckillOrderRequestMapper;
    private final ITradeLocalMessageMapper tradeLocalMessageMapper;
    private final IDiningOrderMapper diningOrderMapper;
    private final IDiningOrderItemMapper diningOrderItemMapper;

    public SeckillRepository(ISeckillActivityMapper seckillActivityMapper,
                             ISeckillOrderMapper seckillOrderMapper,
                             ISeckillOrderRequestMapper seckillOrderRequestMapper,
                             ITradeLocalMessageMapper tradeLocalMessageMapper,
                             IDiningOrderMapper diningOrderMapper,
                             IDiningOrderItemMapper diningOrderItemMapper) {
        this.seckillActivityMapper = seckillActivityMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.seckillOrderRequestMapper = seckillOrderRequestMapper;
        this.tradeLocalMessageMapper = tradeLocalMessageMapper;
        this.diningOrderMapper = diningOrderMapper;
        this.diningOrderItemMapper = diningOrderItemMapper;
    }

    @Override
    public SeckillActivityEntity queryActivityById(Long activityId) {
        SeckillActivityPO po = seckillActivityMapper.selectById(activityId);
        return toActivityEntity(po);
    }

    @Override
    public List<SeckillActivityView> listAvailableActivities(Long packageId, LocalDateTime now, int limit) {
        LambdaQueryWrapper<SeckillActivityPO> wrapper = new LambdaQueryWrapper<SeckillActivityPO>()
                .eq(SeckillActivityPO::getActivityStatus, 1)
                .gt(SeckillActivityPO::getValidEndTime, now)
                .gt(SeckillActivityPO::getStock, 0)
                .orderByAsc(SeckillActivityPO::getValidStartTime)
                .last("limit " + limit);
        if (packageId != null) {
            wrapper.eq(SeckillActivityPO::getPackageId, packageId);
        }
        return seckillActivityMapper.selectList(wrapper)
                .stream()
                .map(po -> toActivityView(po, now))
                .collect(Collectors.toList());
    }

    @Override
    public int queryUserTakeCount(Long activityId, Long userId) {
        Long count = seckillOrderMapper.selectCount(new LambdaQueryWrapper<SeckillOrderPO>()
                .eq(SeckillOrderPO::getActivityId, activityId)
                .eq(SeckillOrderPO::getUserId, userId)
                .in(SeckillOrderPO::getOrderStatus, OrderStatusConstants.WAIT_PAY, OrderStatusConstants.PAID));
        return count == null ? 0 : count.intValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrderResult saveSeckillOrder(SeckillOrderAggregate aggregate, LocalDateTime now) {
        SeckillActivityEntity activity = aggregate.getActivity();
        int stockUpdated = seckillActivityMapper.update(null, new LambdaUpdateWrapper<SeckillActivityPO>()
                .setSql("stock = stock - 1")
                .set(SeckillActivityPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillActivityPO::getId, activity.getId())
                .eq(SeckillActivityPO::getActivityStatus, 1)
                .le(SeckillActivityPO::getValidStartTime, now)
                .gt(SeckillActivityPO::getValidEndTime, now)
                .gt(SeckillActivityPO::getStock, 0));
        if (stockUpdated <= 0) {
            throw new IllegalArgumentException("seckill stock not enough");
        }

        DiningOrderPO orderPO = toOrderPO(aggregate.getOrder());
        diningOrderMapper.insert(orderPO);

        DiningOrderItemEntity orderItem = aggregate.getOrderItem();
        orderItem.setOrderId(orderPO.getId());
        diningOrderItemMapper.insert(toOrderItemPO(orderItem));

        SeckillOrderEntity seckillOrder = aggregate.getSeckillOrder();
        seckillOrder.setOrderId(orderPO.getId());
        seckillOrder.setOrderNo(orderPO.getOrderNo());
        try {
            seckillOrderMapper.insert(toSeckillOrderPO(seckillOrder));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("seckill user take limit", e);
        }

        SeckillActivityPO activityPO = seckillActivityMapper.selectById(activity.getId());
        return toOrderResult(orderPO, activityPO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSeckillOrderRequestAndMessage(SeckillOrderRequestEntity request, TradeLocalMessageEntity message) {
        seckillOrderRequestMapper.insert(toSeckillOrderRequestPO(request));
        tradeLocalMessageMapper.insert(toTradeLocalMessagePO(message));
    }

    @Override
    public SeckillOrderRequestEntity querySeckillOrderRequest(String requestNo) {
        SeckillOrderRequestPO po = seckillOrderRequestMapper.selectOne(new LambdaQueryWrapper<SeckillOrderRequestPO>()
                .eq(SeckillOrderRequestPO::getRequestNo, requestNo)
                .last("limit 1"));
        return toSeckillOrderRequestEntity(po);
    }

    @Override
    public SeckillOrderRequestEntity querySeckillOrderRequestByOrderId(Long orderId) {
        SeckillOrderRequestPO po = seckillOrderRequestMapper.selectOne(new LambdaQueryWrapper<SeckillOrderRequestPO>()
                .eq(SeckillOrderRequestPO::getOrderId, orderId)
                .last("limit 1"));
        return toSeckillOrderRequestEntity(po);
    }

    @Override
    public List<TradeLocalMessageEntity> queryPendingSeckillOrderMessages(LocalDateTime now, int limit) {
        return tradeLocalMessageMapper.selectList(new LambdaQueryWrapper<TradeLocalMessagePO>()
                        .eq(TradeLocalMessagePO::getMessageType, "SECKILL_ORDER_CREATE")
                        .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT)
                        .le(TradeLocalMessagePO::getNextRetryTime, now)
                        .orderByAsc(TradeLocalMessagePO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toTradeLocalMessageEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeLocalMessageEntity> queryProcessingSeckillOrderMessages(LocalDateTime timeoutBefore, int limit) {
        return tradeLocalMessageMapper.selectList(new LambdaQueryWrapper<TradeLocalMessagePO>()
                        .eq(TradeLocalMessagePO::getMessageType, "SECKILL_ORDER_CREATE")
                        .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.PROCESSING)
                        .le(TradeLocalMessagePO::getUpdateTime, timeoutBefore)
                        .orderByAsc(TradeLocalMessagePO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toTradeLocalMessageEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<SeckillOrderRequestEntity> queryTimeoutInitOrProcessingRequests(LocalDateTime timeoutBefore, int limit) {
        return seckillOrderRequestMapper.selectList(new LambdaQueryWrapper<SeckillOrderRequestPO>()
                        .in(SeckillOrderRequestPO::getRequestStatus,
                                SeckillRequestStatusConstants.INIT,
                                SeckillRequestStatusConstants.PROCESSING)
                        .le(SeckillOrderRequestPO::getCreateTime, timeoutBefore)
                        .isNull(SeckillOrderRequestPO::getOrderId)
                        .orderByAsc(SeckillOrderRequestPO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toSeckillOrderRequestEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean markLocalMessageProcessing(Long messageId) {
        int updated = tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.PROCESSING)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId)
                .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT));
        return updated > 0;
    }

    @Override
    public boolean recoverProcessingLocalMessage(Long messageId, LocalDateTime nextRetryTime) {
        int updated = tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .setSql("retry_count = retry_count + 1")
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT)
                .set(TradeLocalMessagePO::getFailReason, "recover stuck processing message")
                .set(TradeLocalMessagePO::getNextRetryTime, nextRetryTime)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId)
                .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.PROCESSING));
        return updated > 0;
    }

    @Override
    public void markLocalMessageSuccess(Long messageId) {
        tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.SUCCESS)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId));
    }

    @Override
    public void markLocalMessageRetry(Long messageId, String failReason, LocalDateTime nextRetryTime) {
        tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .setSql("retry_count = retry_count + 1")
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT)
                .set(TradeLocalMessagePO::getFailReason, limitText(failReason, 512))
                .set(TradeLocalMessagePO::getNextRetryTime, nextRetryTime)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId));
    }

    @Override
    public void markLocalMessageFailed(Long messageId, String failReason) {
        tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.FAILED)
                .set(TradeLocalMessagePO::getFailReason, limitText(failReason, 512))
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId));
    }

    @Override
    public boolean markSeckillOrderRequestProcessing(String requestNo) {
        int updated = seckillOrderRequestMapper.update(null, new LambdaUpdateWrapper<SeckillOrderRequestPO>()
                .set(SeckillOrderRequestPO::getRequestStatus, SeckillRequestStatusConstants.PROCESSING)
                .set(SeckillOrderRequestPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillOrderRequestPO::getRequestNo, requestNo)
                .in(SeckillOrderRequestPO::getRequestStatus,
                        SeckillRequestStatusConstants.INIT,
                        SeckillRequestStatusConstants.FAILED));
        return updated > 0;
    }

    @Override
    public void markSeckillOrderRequestSuccess(String requestNo, SeckillOrderResult result) {
        seckillOrderRequestMapper.update(null, new LambdaUpdateWrapper<SeckillOrderRequestPO>()
                .set(SeckillOrderRequestPO::getRequestStatus, SeckillRequestStatusConstants.SUCCESS)
                .set(SeckillOrderRequestPO::getOrderId, result.getOrderId())
                .set(SeckillOrderRequestPO::getOrderNo, result.getOrderNo())
                .set(SeckillOrderRequestPO::getFailReason, null)
                .set(SeckillOrderRequestPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillOrderRequestPO::getRequestNo, requestNo));
    }

    @Override
    public void markSeckillOrderRequestFailed(String requestNo, String failReason) {
        seckillOrderRequestMapper.update(null, new LambdaUpdateWrapper<SeckillOrderRequestPO>()
                .set(SeckillOrderRequestPO::getRequestStatus, SeckillRequestStatusConstants.FAILED)
                .set(SeckillOrderRequestPO::getFailReason, limitText(failReason, 512))
                .set(SeckillOrderRequestPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillOrderRequestPO::getRequestNo, requestNo));
    }

    @Override
    public boolean cancelTimeoutSeckillOrderRequest(String requestNo, String failReason) {
        int updated = seckillOrderRequestMapper.update(null, new LambdaUpdateWrapper<SeckillOrderRequestPO>()
                .set(SeckillOrderRequestPO::getRequestStatus, SeckillRequestStatusConstants.FAILED)
                .set(SeckillOrderRequestPO::getFailReason, limitText(failReason, 512))
                .set(SeckillOrderRequestPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillOrderRequestPO::getRequestNo, requestNo)
                .isNull(SeckillOrderRequestPO::getOrderId)
                .in(SeckillOrderRequestPO::getRequestStatus,
                        SeckillRequestStatusConstants.INIT,
                        SeckillRequestStatusConstants.PROCESSING));
        return updated > 0;
    }

    @Override
    public int querySeckillOrderCount(Long activityId, String orderStatus) {
        Long count = seckillOrderMapper.selectCount(new LambdaQueryWrapper<SeckillOrderPO>()
                .eq(SeckillOrderPO::getActivityId, activityId)
                .eq(SeckillOrderPO::getOrderStatus, orderStatus));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public Long querySeckillActivityId(DiningOrderEntity order) {
        return querySeckillOrderPO(order).getActivityId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long settlementSeckillPaySuccess(DiningOrderEntity order) {
        SeckillOrderPO seckillOrderPO = querySeckillOrderPO(order);

        int orderUpdated = diningOrderMapper.update(null, new LambdaUpdateWrapper<DiningOrderPO>()
                .set(DiningOrderPO::getOrderStatus, OrderStatusConstants.PAID)
                .set(DiningOrderPO::getUpdateTime, LocalDateTime.now())
                .eq(DiningOrderPO::getId, order.getId())
                .eq(DiningOrderPO::getUserId, order.getUserId())
                .eq(DiningOrderPO::getOrderStatus, OrderStatusConstants.WAIT_PAY));
        if (orderUpdated <= 0) {
            throw new IllegalArgumentException("order status can not pay");
        }

        int seckillOrderUpdated = seckillOrderMapper.update(null, new LambdaUpdateWrapper<SeckillOrderPO>()
                .set(SeckillOrderPO::getOrderStatus, OrderStatusConstants.PAID)
                .set(SeckillOrderPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillOrderPO::getId, seckillOrderPO.getId())
                .eq(SeckillOrderPO::getOrderStatus, OrderStatusConstants.WAIT_PAY));
        if (seckillOrderUpdated <= 0) {
            throw new IllegalArgumentException("seckill order status can not pay");
        }
        return seckillOrderPO.getActivityId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelUnpaidSeckillOrder(DiningOrderEntity order) {
        SeckillOrderPO seckillOrderPO = querySeckillOrderPO(order);

        int orderUpdated = diningOrderMapper.update(null, new LambdaUpdateWrapper<DiningOrderPO>()
                .set(DiningOrderPO::getOrderStatus, OrderStatusConstants.CANCELED)
                .set(DiningOrderPO::getUpdateTime, LocalDateTime.now())
                .eq(DiningOrderPO::getId, order.getId())
                .eq(DiningOrderPO::getUserId, order.getUserId())
                .eq(DiningOrderPO::getOrderStatus, OrderStatusConstants.WAIT_PAY));
        if (orderUpdated <= 0) {
            throw new IllegalArgumentException("order status can not cancel");
        }

        int seckillOrderUpdated = seckillOrderMapper.update(null, new LambdaUpdateWrapper<SeckillOrderPO>()
                .set(SeckillOrderPO::getOrderStatus, OrderStatusConstants.CANCELED)
                .set(SeckillOrderPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillOrderPO::getId, seckillOrderPO.getId())
                .eq(SeckillOrderPO::getOrderStatus, OrderStatusConstants.WAIT_PAY));
        if (seckillOrderUpdated <= 0) {
            throw new IllegalArgumentException("seckill order status can not cancel");
        }

        seckillActivityMapper.update(null, new LambdaUpdateWrapper<SeckillActivityPO>()
                .setSql("stock = stock + 1")
                .set(SeckillActivityPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillActivityPO::getId, seckillOrderPO.getActivityId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrderEntity refundPaidSeckillOrder(DiningOrderEntity order) {
        SeckillOrderPO seckillOrderPO = querySeckillOrderPO(order);

        int orderUpdated = diningOrderMapper.update(null, new LambdaUpdateWrapper<DiningOrderPO>()
                .set(DiningOrderPO::getOrderStatus, OrderStatusConstants.REFUNDED)
                .set(DiningOrderPO::getUpdateTime, LocalDateTime.now())
                .eq(DiningOrderPO::getId, order.getId())
                .eq(DiningOrderPO::getUserId, order.getUserId())
                .eq(DiningOrderPO::getOrderStatus, OrderStatusConstants.PAID));
        if (orderUpdated <= 0) {
            throw new IllegalArgumentException("order status can not refund");
        }

        int seckillOrderUpdated = seckillOrderMapper.update(null, new LambdaUpdateWrapper<SeckillOrderPO>()
                .set(SeckillOrderPO::getOrderStatus, OrderStatusConstants.REFUNDED)
                .set(SeckillOrderPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillOrderPO::getId, seckillOrderPO.getId())
                .eq(SeckillOrderPO::getOrderStatus, OrderStatusConstants.PAID));
        if (seckillOrderUpdated <= 0) {
            throw new IllegalArgumentException("seckill order status can not refund");
        }

        seckillActivityMapper.update(null, new LambdaUpdateWrapper<SeckillActivityPO>()
                .setSql("stock = stock + 1")
                .set(SeckillActivityPO::getUpdateTime, LocalDateTime.now())
                .eq(SeckillActivityPO::getId, seckillOrderPO.getActivityId()));

        SeckillOrderPO refreshed = seckillOrderMapper.selectById(seckillOrderPO.getId());
        return toSeckillOrderEntity(refreshed);
    }

    private SeckillOrderPO querySeckillOrderPO(DiningOrderEntity order) {
        SeckillOrderPO po = seckillOrderMapper.selectOne(new LambdaQueryWrapper<SeckillOrderPO>()
                .eq(SeckillOrderPO::getOrderId, order.getId())
                .eq(SeckillOrderPO::getUserId, order.getUserId())
                .last("limit 1"));
        if (po == null) {
            throw new IllegalArgumentException("seckill order not found");
        }
        return po;
    }

    private SeckillOrderEntity toSeckillOrderEntity(SeckillOrderPO po) {
        if (po == null) {
            return null;
        }
        SeckillOrderEntity entity = new SeckillOrderEntity();
        entity.setId(po.getId());
        entity.setUserId(po.getUserId());
        entity.setActivityId(po.getActivityId());
        entity.setPackageId(po.getPackageId());
        entity.setOrderId(po.getOrderId());
        entity.setOrderNo(po.getOrderNo());
        entity.setOrderStatus(po.getOrderStatus());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private SeckillActivityEntity toActivityEntity(SeckillActivityPO po) {
        if (po == null) {
            return null;
        }
        SeckillActivityEntity entity = new SeckillActivityEntity();
        entity.setId(po.getId());
        entity.setPackageId(po.getPackageId());
        entity.setActivityName(po.getActivityName());
        entity.setSeckillPrice(po.getSeckillPrice());
        entity.setActivityStatus(po.getActivityStatus());
        entity.setValidStartTime(po.getValidStartTime());
        entity.setValidEndTime(po.getValidEndTime());
        entity.setStock(po.getStock());
        entity.setUserTakeLimit(po.getUserTakeLimit());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private SeckillActivityView toActivityView(SeckillActivityPO po, LocalDateTime now) {
        SeckillActivityView view = new SeckillActivityView();
        view.setActivityId(po.getId());
        view.setPackageId(po.getPackageId());
        view.setActivityName(po.getActivityName());
        view.setSeckillPrice(po.getSeckillPrice());
        view.setActivityStatus(po.getActivityStatus());
        view.setValidStartTime(po.getValidStartTime());
        view.setValidEndTime(po.getValidEndTime());
        view.setStock(po.getStock());
        view.setUserTakeLimit(po.getUserTakeLimit());
        view.setCanBuy(po.getActivityStatus() != null
                && po.getActivityStatus() == 1
                && !po.getValidStartTime().isAfter(now)
                && po.getValidEndTime().isAfter(now)
                && po.getStock() != null
                && po.getStock() > 0);
        return view;
    }

    private SeckillOrderResult toOrderResult(DiningOrderPO orderPO, SeckillActivityPO activityPO) {
        SeckillOrderResult result = new SeckillOrderResult();
        result.setActivityId(activityPO.getId());
        result.setPackageId(activityPO.getPackageId());
        result.setOrderId(orderPO.getId());
        result.setOrderNo(orderPO.getOrderNo());
        result.setPayAmount(orderPO.getPayAmount());
        result.setOrderStatus(orderPO.getOrderStatus());
        result.setRemainingStock(activityPO.getStock());
        return result;
    }

    private SeckillOrderPO toSeckillOrderPO(SeckillOrderEntity entity) {
        SeckillOrderPO po = new SeckillOrderPO();
        po.setId(entity.getId());
        po.setUserId(entity.getUserId());
        po.setActivityId(entity.getActivityId());
        po.setPackageId(entity.getPackageId());
        po.setOrderId(entity.getOrderId());
        po.setOrderNo(entity.getOrderNo());
        po.setOrderStatus(entity.getOrderStatus());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private SeckillOrderRequestPO toSeckillOrderRequestPO(SeckillOrderRequestEntity entity) {
        SeckillOrderRequestPO po = new SeckillOrderRequestPO();
        po.setId(entity.getId());
        po.setRequestNo(entity.getRequestNo());
        po.setUserId(entity.getUserId());
        po.setActivityId(entity.getActivityId());
        po.setPackageId(entity.getPackageId());
        po.setQuantity(entity.getQuantity());
        po.setOrderId(entity.getOrderId());
        po.setOrderNo(entity.getOrderNo());
        po.setRequestStatus(entity.getRequestStatus());
        po.setFailReason(entity.getFailReason());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private SeckillOrderRequestEntity toSeckillOrderRequestEntity(SeckillOrderRequestPO po) {
        if (po == null) {
            return null;
        }
        SeckillOrderRequestEntity entity = new SeckillOrderRequestEntity();
        entity.setId(po.getId());
        entity.setRequestNo(po.getRequestNo());
        entity.setUserId(po.getUserId());
        entity.setActivityId(po.getActivityId());
        entity.setPackageId(po.getPackageId());
        entity.setQuantity(po.getQuantity());
        entity.setOrderId(po.getOrderId());
        entity.setOrderNo(po.getOrderNo());
        entity.setRequestStatus(po.getRequestStatus());
        entity.setFailReason(po.getFailReason());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private TradeLocalMessagePO toTradeLocalMessagePO(TradeLocalMessageEntity entity) {
        TradeLocalMessagePO po = new TradeLocalMessagePO();
        po.setId(entity.getId());
        po.setMessageId(entity.getMessageId());
        po.setMessageType(entity.getMessageType());
        po.setBizType(entity.getBizType());
        po.setBizId(entity.getBizId());
        po.setMessageStatus(entity.getMessageStatus());
        po.setRetryCount(entity.getRetryCount());
        po.setMaxRetryCount(entity.getMaxRetryCount());
        po.setNextRetryTime(entity.getNextRetryTime());
        po.setContent(entity.getContent());
        po.setFailReason(entity.getFailReason());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private TradeLocalMessageEntity toTradeLocalMessageEntity(TradeLocalMessagePO po) {
        TradeLocalMessageEntity entity = new TradeLocalMessageEntity();
        entity.setId(po.getId());
        entity.setMessageId(po.getMessageId());
        entity.setMessageType(po.getMessageType());
        entity.setBizType(po.getBizType());
        entity.setBizId(po.getBizId());
        entity.setMessageStatus(po.getMessageStatus());
        entity.setRetryCount(po.getRetryCount());
        entity.setMaxRetryCount(po.getMaxRetryCount());
        entity.setNextRetryTime(po.getNextRetryTime());
        entity.setContent(po.getContent());
        entity.setFailReason(po.getFailReason());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private String limitText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private DiningOrderPO toOrderPO(DiningOrderEntity entity) {
        DiningOrderPO po = new DiningOrderPO();
        po.setId(entity.getId());
        po.setOrderNo(entity.getOrderNo());
        po.setUserId(entity.getUserId());
        po.setShopId(entity.getShopId());
        po.setPackageId(entity.getPackageId());
        po.setQuantity(entity.getQuantity());
        po.setTotalAmount(entity.getTotalAmount());
        po.setPayAmount(entity.getPayAmount());
        po.setTradeType(entity.getTradeType());
        po.setOrderStatus(entity.getOrderStatus());
        po.setUseTime(entity.getUseTime());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private DiningOrderItemPO toOrderItemPO(DiningOrderItemEntity entity) {
        DiningOrderItemPO po = new DiningOrderItemPO();
        po.setId(entity.getId());
        po.setOrderId(entity.getOrderId());
        po.setShopId(entity.getShopId());
        po.setShopNameSnapshot(entity.getShopNameSnapshot());
        po.setPackageId(entity.getPackageId());
        po.setPackageNameSnapshot(entity.getPackageNameSnapshot());
        po.setPackageDescriptionSnapshot(entity.getPackageDescriptionSnapshot());
        po.setCoverImageSnapshot(entity.getCoverImageSnapshot());
        po.setPackagePriceSnapshot(entity.getPackagePriceSnapshot());
        po.setActualPrice(entity.getActualPrice());
        po.setQuantity(entity.getQuantity());
        po.setUseRuleSnapshot(entity.getUseRuleSnapshot());
        return po;
    }
}
