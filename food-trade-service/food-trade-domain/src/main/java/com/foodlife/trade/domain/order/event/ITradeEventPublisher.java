package com.foodlife.trade.domain.order.event;

public interface ITradeEventPublisher {

    void publish(String topic, String tag, String key, Object payload);

    int retryPendingEvents(Integer limit);
}

