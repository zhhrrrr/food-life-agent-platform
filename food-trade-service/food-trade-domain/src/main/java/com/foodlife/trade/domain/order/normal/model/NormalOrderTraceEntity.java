package com.foodlife.trade.domain.order.normal.model;

import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class NormalOrderTraceEntity implements Serializable {

    private DiningOrderEntity order;
    private List<DiningOrderItemEntity> orderItems;
    private PackageTradeSnapshot packageSnapshot;
    private List<TradeLocalMessageEntity> stockMessages;
    private List<PackageStockChangeRecord> stockChangeRecords;
    private String currentStage;
}
