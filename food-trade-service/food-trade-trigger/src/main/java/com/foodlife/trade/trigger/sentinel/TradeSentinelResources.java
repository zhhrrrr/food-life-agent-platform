package com.foodlife.trade.trigger.sentinel;

public final class TradeSentinelResources {

    public static final String NORMAL_ORDER_CREATE = "trade.order.normal.create";
    public static final String GROUP_BUY_ORDER_CREATE = "trade.order.groupBuy.create";
    public static final String SECKILL_ORDER_CREATE = "trade.order.seckill.create";
    public static final String SECKILL_ORDER_ASYNC_CREATE = "trade.order.seckill.asyncCreate";
    public static final String USER_ORDER_CREATE = "trade.order.user.create";
    public static final String SECKILL_STOCK_OCCUPY = "trade.seckill.stock.occupy";
    public static final String PAYMENT_CALLBACK = "trade.payment.callback";
    public static final String BUSINESS_PACKAGE_SNAPSHOT = "trade.feign.business.package.snapshot";
    public static final String BUSINESS_PACKAGE_STOCK = "trade.feign.business.package.stock";

    private TradeSentinelResources() {
    }
}
