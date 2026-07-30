package com.foodlife.patterns.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessStrategyRouter {

    private final List<BusinessStrategy<?, ?>> strategies;

    public BusinessStrategyRouter(List<BusinessStrategy<?, ?>> strategies) {
        this.strategies = strategies;
    }

    @SuppressWarnings("unchecked")
    public <C, R> R apply(String group, C context) {
        for (BusinessStrategy<?, ?> strategy : strategies) {
            BusinessStrategy<C, R> typedStrategy = (BusinessStrategy<C, R>) strategy;
            if (group.equals(typedStrategy.group()) && typedStrategy.support(context)) {
                return typedStrategy.apply(context);
            }
        }
        throw new IllegalArgumentException("business strategy not found");
    }
}
