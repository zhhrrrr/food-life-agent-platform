package com.foodlife.trade.trigger.app;

import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import org.springframework.stereotype.Service;

@Service
public class RefundConfirmApplicationService {

    private final OrderDomainService orderDomainService;

    public RefundConfirmApplicationService(OrderDomainService orderDomainService) {
        this.orderDomainService = orderDomainService;
    }

    public OrderRefundBehaviorEntity confirmRefund(OrderRefundCommandEntity command) {
        return orderDomainService.refundOrderMock(command);
    }
}
