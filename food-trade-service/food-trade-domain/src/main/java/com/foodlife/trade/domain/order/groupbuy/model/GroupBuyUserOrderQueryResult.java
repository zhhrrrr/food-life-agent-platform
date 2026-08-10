package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GroupBuyUserOrderQueryResult implements Serializable {

    private List<GroupBuyUserOrderView> orders = new ArrayList<>();
    private Boolean hasMore;
    private Long lastId;
}
