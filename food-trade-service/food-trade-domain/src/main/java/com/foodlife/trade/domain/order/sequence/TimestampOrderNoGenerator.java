package com.foodlife.trade.domain.order.sequence;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class TimestampOrderNoGenerator implements OrderNoGenerator {

    @Override
    public String generate(Long userId) {
        return "NO"
                + System.currentTimeMillis()
                + normalizeUserId(userId)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String normalizeUserId(Long userId) {
        return userId == null ? "0" : String.valueOf(userId);
    }
}
