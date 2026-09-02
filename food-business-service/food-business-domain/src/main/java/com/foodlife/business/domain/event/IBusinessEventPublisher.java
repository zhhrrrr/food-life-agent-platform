package com.foodlife.business.domain.event;

public interface IBusinessEventPublisher {

    void publish(String topic, String tag, String key, Object payload);
}

