package com.foodlife.trade.domain.order.event;

public final class TradeMqTopics {

    public static final String TRADE_ORDER_TOPIC = "trade_order_topic";
    public static final String PAYMENT_TOPIC = "payment_topic";

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CANCEL_TIMEOUT = "order.cancel.timeout";
    public static final String ORDER_PAID = "order.paid";
    public static final String ORDER_REFUND_REQUESTED = "order.refund.requested";
    public static final String ORDER_USED = "order.used";

    public static final String PAYMENT_CREATED = "payment.created";
    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String PAYMENT_CLOSED = "payment.closed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";

    private TradeMqTopics() {
    }
}

