package com.foodlife.trade.domain.order.message.repository;

import com.foodlife.trade.domain.order.message.model.MqQueueBacklogEntity;

import java.util.List;

public interface IMqQueueInspector {

    List<MqQueueBacklogEntity> listTradeQueues();
}
