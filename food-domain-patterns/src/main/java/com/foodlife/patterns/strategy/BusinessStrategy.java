package com.foodlife.patterns.strategy;

public interface BusinessStrategy<C, R> {

    String group();

    boolean support(C context);

    R apply(C context);
}
