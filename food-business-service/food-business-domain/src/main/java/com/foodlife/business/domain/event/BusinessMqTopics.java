package com.foodlife.business.domain.event;

public final class BusinessMqTopics {

    public static final String PACKAGE_STOCK_TOPIC = "package_stock_topic";
    public static final String SHOP_REVIEW_TOPIC = "shop_review_topic";

    public static final String STOCK_OCCUPIED = "stock.occupied";
    public static final String STOCK_RELEASED = "stock.released";
    public static final String STOCK_SOLD_CONFIRMED = "stock.sold.confirmed";
    public static final String STOCK_ROLLBACK = "stock.rollback";

    public static final String REVIEW_CREATED = "review.created";

    private BusinessMqTopics() {
    }
}

