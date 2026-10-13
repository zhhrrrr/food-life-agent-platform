package com.foodlife.trade.domain.order.service.refund;

import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.model.*;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.payment.service.*;
import com.foodlife.trade.domain.order.seckill.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderRefundService {
    private final RefundTransactionService transactions;
    private final IPaymentOrderRepository payments;
    private final PaymentRefundService refunds;
    private final RefundJournalService journal;
    private final ISeckillRepository seckillOrders;
    private final ISeckillStockRepository seckillStock;

    public OrderRefundService(RefundTransactionService transactions, IPaymentOrderRepository payments,
            PaymentRefundService refunds, RefundJournalService journal,
            ISeckillRepository seckillOrders, ISeckillStockRepository seckillStock) {
        this.transactions = transactions;
        this.payments = payments;
        this.refunds = refunds;
        this.journal = journal;
        this.seckillOrders = seckillOrders;
        this.seckillStock = seckillStock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command) {
        DiningOrderEntity order = transactions.prepare(command);
        PaymentOrderEntity payment = payments.findByOrderIdAndUserId(order.getId(), order.getUserId());
        RefundOrderEntity refund = payment == null ? null
                : refunds.refundPaidPayment(payment, command.getSource(), command.getRefundReason());
        OrderRefundBehaviorEntity result = transactions.complete(command);
        if (refund != null) {
            result.setPaymentRefunded(true);
            result.setRefundOrderNo(refund.getRefundOrderNo());
            result.setOutRefundNo(refund.getOutRefundNo());
        }
        // Database compensation has committed. Redis is replayable by the durable refund journal.
        if (TradeTypeConstants.SECKILL.equals(order.getTradeType())
                && (refund == null || !Boolean.TRUE.equals(refund.getBusinessCompleted()))) {
            seckillStock.releaseActivityStock(seckillOrders.querySeckillActivityId(order), order.getUserId(),
                    "REFUND:" + order.getOrderNo());
        }
        if (refund != null) { journal.businessCompleted(refund.getRefundOrderNo()); }
        return result;
    }
}
