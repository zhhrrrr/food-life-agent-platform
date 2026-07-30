package com.foodlife.trade.domain.order.sequence;

import org.springframework.stereotype.Component;

@Component
public class TimestampOrderNoGenerator implements OrderNoGenerator {

    @Override
    public String generate(Long userId) {
        return "NO" + System.currentTimeMillis() + userId;
    }
}
