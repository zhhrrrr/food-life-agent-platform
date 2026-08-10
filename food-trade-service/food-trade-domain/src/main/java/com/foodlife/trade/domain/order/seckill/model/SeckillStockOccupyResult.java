package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

@Data
public class SeckillStockOccupyResult {

    public static final String SUCCESS = "SUCCESS";
    public static final String ACTIVITY_NOT_PREHEATED = "ACTIVITY_NOT_PREHEATED";
    public static final String ACTIVITY_DISABLED = "ACTIVITY_DISABLED";
    public static final String ACTIVITY_NOT_START = "ACTIVITY_NOT_START";
    public static final String ACTIVITY_ENDED = "ACTIVITY_ENDED";
    public static final String STOCK_NOT_ENOUGH = "STOCK_NOT_ENOUGH";
    public static final String USER_TAKE_LIMIT = "USER_TAKE_LIMIT";

    private Long activityId;
    private Long userId;
    private Boolean success;
    private Integer remainingStock;
    private String rejectCode;
    private String rejectMessage;
}
