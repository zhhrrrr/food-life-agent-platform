package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GroupBuyTimeoutCompensateResult implements Serializable {

    private LocalDateTime compensateTime;
    private Integer scannedTeamCount;
    private Integer compensatedTeamCount;
    private Integer canceledOrderCount;
    private Integer refundedOrderCount;
    private Integer restoredStockCount;
    private List<GroupBuyTimeoutCompensateDetail> details = new ArrayList<>();
}
