package com.foodlife.trade.domain.order.create;

import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.CreateOrderResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderCreateTemplateRouter {

    private final List<OrderCreateTemplate> templates;

    public OrderCreateTemplateRouter(List<OrderCreateTemplate> templates) {
        this.templates = templates;
    }

    public CreateOrderResult create(String tradeType, CreateOrderCommand command) {
        return templates.stream()
                .filter(template -> template.support(tradeType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("trade type not supported"))
                .create(command);
    }
}
