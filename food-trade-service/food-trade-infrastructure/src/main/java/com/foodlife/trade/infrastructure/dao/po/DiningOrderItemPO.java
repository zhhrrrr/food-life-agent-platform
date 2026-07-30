package com.foodlife.trade.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("dining_order_item")
public class DiningOrderItemPO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
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
