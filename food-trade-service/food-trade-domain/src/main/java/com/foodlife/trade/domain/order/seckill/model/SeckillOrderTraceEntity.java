package com.foodlife.trade.domain.order.seckill.model;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.DiningOrderItemEntity;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SeckillOrderTraceEntity implements Serializable {

    private SeckillOrderRequestEntity request;
    private DiningOrderEntity order;
    private List<DiningOrderItemEntity> orderItems;
    private SeckillActivityEntity activity;
    private PackageTradeSnapshot packageSnapshot;
    private Integer dbStock;
    private Integer redisStock;
    private Integer waitPayCount;
    private Integer paidCount;
    private Boolean orderCreated;
    private String currentStage;
}
