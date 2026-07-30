package com.foodlife.patterns.chain;

public interface BusinessChainHandler<C> {

    String group();

    int order();

    default boolean support(C context) {
        return true;
    }

    void handle(C context);
}
