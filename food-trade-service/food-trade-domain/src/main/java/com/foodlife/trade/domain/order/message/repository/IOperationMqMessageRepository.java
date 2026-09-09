package com.foodlife.trade.domain.order.message.repository;

import com.foodlife.trade.domain.order.message.model.OperationMqMessageQuery;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;

import java.util.List;

public interface IOperationMqMessageRepository {

    List<TradeLocalMessageEntity> listMessages(OperationMqMessageQuery query);
}
