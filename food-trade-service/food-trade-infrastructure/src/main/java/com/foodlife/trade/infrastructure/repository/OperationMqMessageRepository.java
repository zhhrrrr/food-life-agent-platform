package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodlife.trade.domain.order.message.model.OperationMqMessageQuery;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.message.repository.IOperationMqMessageRepository;
import com.foodlife.trade.infrastructure.dao.ITradeLocalMessageMapper;
import com.foodlife.trade.infrastructure.dao.po.TradeLocalMessagePO;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class OperationMqMessageRepository implements IOperationMqMessageRepository {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ITradeLocalMessageMapper tradeLocalMessageMapper;

    public OperationMqMessageRepository(ITradeLocalMessageMapper tradeLocalMessageMapper) {
        this.tradeLocalMessageMapper = tradeLocalMessageMapper;
    }

    @Override
    public List<TradeLocalMessageEntity> listMessages(OperationMqMessageQuery query) {
        String messageStatus = trim(query == null ? null : query.getMessageStatus());
        String messageType = trim(query == null ? null : query.getMessageType());
        String bizId = trim(query == null ? null : query.getBizId());
        int limit = normalizeLimit(query == null ? null : query.getLimit());
        LambdaQueryWrapper<TradeLocalMessagePO> wrapper = new LambdaQueryWrapper<TradeLocalMessagePO>()
                .eq(StringUtils.hasText(messageStatus), TradeLocalMessagePO::getMessageStatus, messageStatus)
                .eq(StringUtils.hasText(messageType), TradeLocalMessagePO::getMessageType, messageType)
                .eq(StringUtils.hasText(bizId), TradeLocalMessagePO::getBizId, bizId)
                .orderByDesc(TradeLocalMessagePO::getId)
                .last("limit " + limit);
        return tradeLocalMessageMapper.selectList(wrapper)
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private TradeLocalMessageEntity toEntity(TradeLocalMessagePO po) {
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
}
