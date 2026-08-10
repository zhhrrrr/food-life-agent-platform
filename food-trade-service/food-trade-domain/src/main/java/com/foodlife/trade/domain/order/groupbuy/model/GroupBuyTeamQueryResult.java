package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GroupBuyTeamQueryResult implements Serializable {

    private List<GroupBuyTeamView> teams = new ArrayList<>();
}
