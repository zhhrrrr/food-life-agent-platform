package com.foodlife.patterns.chain;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
public class BusinessChainRouter {

    private final List<BusinessChainHandler<?>> handlers;

    public BusinessChainRouter(List<BusinessChainHandler<?>> handlers) {
        this.handlers = handlers;
    }

    public <C> void execute(String group, C context) {
        List<BusinessChainHandler<C>> matchedHandlers = matchHandlers(group, context);
        Collections.sort(matchedHandlers, Comparator.comparingInt(BusinessChainHandler::order));
        for (BusinessChainHandler<C> handler : matchedHandlers) {
            handler.handle(context);
        }
    }

    @SuppressWarnings("unchecked")
    private <C> List<BusinessChainHandler<C>> matchHandlers(String group, C context) {
        List<BusinessChainHandler<C>> matchedHandlers = new ArrayList<>();
        for (BusinessChainHandler<?> handler : handlers) {
            BusinessChainHandler<C> typedHandler = (BusinessChainHandler<C>) handler;
            if (group.equals(typedHandler.group()) && typedHandler.support(context)) {
                matchedHandlers.add(typedHandler);
            }
        }
        return matchedHandlers;
    }
}
