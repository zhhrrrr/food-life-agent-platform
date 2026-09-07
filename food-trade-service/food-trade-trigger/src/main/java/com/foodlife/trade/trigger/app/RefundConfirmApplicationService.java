package com.foodlife.trade.trigger.app;

import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;

@Service
public class RefundConfirmApplicationService {

    private final OrderDomainService orderDomainService;

    public RefundConfirmApplicationService(OrderDomainService orderDomainService) {
        this.orderDomainService = orderDomainService;
    }

    @GlobalTransactional(name = "food-refund-confirm", rollbackFor = Exception.class)
    public OrderRefundBehaviorEntity confirmRefund(OrderRefundCommandEntity command) {
        return orderDomainService.refundOrderMock(command);
    }
}
