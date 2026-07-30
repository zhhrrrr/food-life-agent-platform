package com.foodlife.trade.domain.order.sequence;

public interface OrderNoGenerator {

    String generate(Long userId);
}
