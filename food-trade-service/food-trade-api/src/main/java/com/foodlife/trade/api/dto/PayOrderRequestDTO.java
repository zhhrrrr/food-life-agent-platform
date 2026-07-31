package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PayOrderRequestDTO implements Serializable {

    private String source;
    private String channel;
    private String outTradeNo;
}
