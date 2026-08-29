package com.foodlife.trade.domain.order.seckill.repository;

import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityView;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderAggregate;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderRequestEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface ISeckillRepository {

    SeckillActivityEntity queryActivityById(Long activityId);

    List<SeckillActivityView> listAvailableActivities(Long packageId, LocalDateTime now, int limit);

    int queryUserTakeCount(Long activityId, Long userId);

    SeckillOrderResult saveSeckillOrder(SeckillOrderAggregate aggregate, LocalDateTime now);

    void saveSeckillOrderRequestAndMessage(SeckillOrderRequestEntity request, TradeLocalMessageEntity message);

    SeckillOrderRequestEntity querySeckillOrderRequest(String requestNo);

    SeckillOrderRequestEntity querySeckillOrderRequestByOrderId(Long orderId);

    List<TradeLocalMessageEntity> queryPendingSeckillOrderMessages(LocalDateTime now, int limit);

    List<TradeLocalMessageEntity> queryProcessingSeckillOrderMessages(LocalDateTime timeoutBefore, int limit);

    List<SeckillOrderRequestEntity> queryTimeoutInitOrProcessingRequests(LocalDateTime timeoutBefore, int limit);

    boolean markLocalMessageProcessing(Long messageId);

    boolean recoverProcessingLocalMessage(Long messageId, LocalDateTime nextRetryTime);

    void markLocalMessageSuccess(Long messageId);

    void markLocalMessageRetry(Long messageId, String failReason, LocalDateTime nextRetryTime);

    void markLocalMessageFailed(Long messageId, String failReason);

    boolean markSeckillOrderRequestProcessing(String requestNo);

    void markSeckillOrderRequestSuccess(String requestNo, SeckillOrderResult result);

    void markSeckillOrderRequestFailed(String requestNo, String failReason);

    boolean cancelTimeoutSeckillOrderRequest(String requestNo, String failReason);

    int querySeckillOrderCount(Long activityId, String orderStatus);

    Long querySeckillActivityId(DiningOrderEntity order);

    Long settlementSeckillPaySuccess(DiningOrderEntity order);

    void cancelUnpaidSeckillOrder(DiningOrderEntity order);

    SeckillOrderEntity refundPaidSeckillOrder(DiningOrderEntity order);
}
