package com.foodlife.patterns.rule;

public interface RuleNode<C, R> {

    String key();

    R apply(C context);

    default String next(C context, R result) {
        return null;
    }
}
