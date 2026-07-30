package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class DiningOrderItemEntity implements Serializable {

    private Long id;
    private Long orderId;
    private Long shopId;
    private String shopNameSnapshot;
    private Long packageId;
    private String packageNameSnapshot;
    private String packageDescriptionSnapshot;
    private String coverImageSnapshot;
    private Long packagePriceSnapshot;
    private Long actualPrice;
    private Integer quantity;
    private String useRuleSnapshot;
}
