package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderItemResponseDTO implements Serializable {

    private Long itemId;
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
