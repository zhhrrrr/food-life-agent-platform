package com.foodlife.trade.domain.order.normal.repository;

import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface INormalPackageStockMessageRepository {

    TradeLocalMessageEntity saveInitMessage(String messageType, String bizId, String content, LocalDateTime now);

    TradeLocalMessageEntity queryMessageByMessageId(String messageId);

    List<TradeLocalMessageEntity> queryPendingMessages(LocalDateTime now, int limit);

    List<TradeLocalMessageEntity> queryProcessingMessages(LocalDateTime timeoutBefore, int limit);

    List<TradeLocalMessageEntity> queryMessages(String bizId, String messageStatus, int limit);

    boolean markMessageProcessing(Long messageId);

    boolean recoverProcessingMessage(Long messageId, LocalDateTime nextRetryTime);

    void markMessageSuccess(Long messageId);

    void markMessageRetry(Long messageId, String failReason, LocalDateTime nextRetryTime);

    void markMessageFailed(Long messageId, String failReason);
}
