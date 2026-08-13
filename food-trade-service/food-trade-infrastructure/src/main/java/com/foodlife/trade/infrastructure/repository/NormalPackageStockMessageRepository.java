package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.message.constant.LocalMessageStatusConstants;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.normal.constant.NormalPackageStockMessageConstants;
import com.foodlife.trade.domain.order.normal.repository.INormalPackageStockMessageRepository;
import com.foodlife.trade.infrastructure.dao.ITradeLocalMessageMapper;
import com.foodlife.trade.infrastructure.dao.po.TradeLocalMessagePO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class NormalPackageStockMessageRepository implements INormalPackageStockMessageRepository {

    private final ITradeLocalMessageMapper tradeLocalMessageMapper;

    public NormalPackageStockMessageRepository(ITradeLocalMessageMapper tradeLocalMessageMapper) {
        this.tradeLocalMessageMapper = tradeLocalMessageMapper;
    }

    @Override
    public TradeLocalMessageEntity saveInitMessage(String messageType, String bizId, String content, LocalDateTime now) {
        TradeLocalMessagePO po = new TradeLocalMessagePO();
        po.setMessageId(buildMessageId(messageType, bizId));
        po.setMessageType(messageType);
        po.setBizType(NormalPackageStockMessageConstants.BIZ_TYPE);
        po.setBizId(bizId);
        po.setMessageStatus(LocalMessageStatusConstants.INIT);
        po.setRetryCount(0);
        po.setMaxRetryCount(3);
        po.setNextRetryTime(now);
        po.setContent(content);
        po.setCreateTime(now);
        po.setUpdateTime(now);
        tradeLocalMessageMapper.insert(po);
        return toEntity(po);
    }

    @Override
    public TradeLocalMessageEntity queryMessageByMessageId(String messageId) {
        return toEntity(tradeLocalMessageMapper.selectOne(new LambdaQueryWrapper<TradeLocalMessagePO>()
                .eq(TradeLocalMessagePO::getMessageId, messageId)
                .last("limit 1")));
    }

    @Override
    public List<TradeLocalMessageEntity> queryPendingMessages(LocalDateTime now, int limit) {
        return tradeLocalMessageMapper.selectList(new LambdaQueryWrapper<TradeLocalMessagePO>()
                        .in(TradeLocalMessagePO::getMessageType, normalMessageTypes())
                        .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT)
                        .le(TradeLocalMessagePO::getNextRetryTime, now)
                        .orderByAsc(TradeLocalMessagePO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeLocalMessageEntity> queryProcessingMessages(LocalDateTime timeoutBefore, int limit) {
        return tradeLocalMessageMapper.selectList(new LambdaQueryWrapper<TradeLocalMessagePO>()
                        .in(TradeLocalMessagePO::getMessageType, normalMessageTypes())
                        .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.PROCESSING)
                        .le(TradeLocalMessagePO::getUpdateTime, timeoutBefore)
                        .orderByAsc(TradeLocalMessagePO::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean markMessageProcessing(Long messageId) {
        int updated = tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.PROCESSING)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId)
                .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT));
        return updated > 0;
    }

    @Override
    public boolean recoverProcessingMessage(Long messageId, LocalDateTime nextRetryTime) {
        int updated = tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .setSql("retry_count = retry_count + 1")
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT)
                .set(TradeLocalMessagePO::getFailReason, "recover normal package stock message")
                .set(TradeLocalMessagePO::getNextRetryTime, nextRetryTime)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId)
                .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.PROCESSING));
        return updated > 0;
    }

    @Override
    public void markMessageSuccess(Long messageId) {
        tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.SUCCESS)
                .set(TradeLocalMessagePO::getFailReason, null)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId));
    }

    @Override
    public void markMessageRetry(Long messageId, String failReason, LocalDateTime nextRetryTime) {
        tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .setSql("retry_count = retry_count + 1")
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT)
                .set(TradeLocalMessagePO::getFailReason, limitText(failReason))
                .set(TradeLocalMessagePO::getNextRetryTime, nextRetryTime)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId));
    }

    @Override
    public void markMessageFailed(Long messageId, String failReason) {
        tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.FAILED)
                .set(TradeLocalMessagePO::getFailReason, limitText(failReason))
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, messageId));
    }

    private String buildMessageId(String messageType, String bizId) {
        return messageType + ":" + bizId;
    }

    private List<String> normalMessageTypes() {
        return Arrays.asList(
                NormalPackageStockMessageConstants.RELEASE_STOCK,
                NormalPackageStockMessageConstants.CONFIRM_SOLD,
                NormalPackageStockMessageConstants.ROLLBACK_SOLD
        );
    }

    private TradeLocalMessageEntity toEntity(TradeLocalMessagePO po) {
        if (po == null) {
            return null;
        }
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

    private String limitText(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 512 ? text : text.substring(0, 512);
    }
}
