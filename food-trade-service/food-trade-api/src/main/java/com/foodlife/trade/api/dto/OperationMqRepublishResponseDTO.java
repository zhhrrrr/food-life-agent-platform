package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OperationMqRepublishResponseDTO implements Serializable {

    private String messageId;
    private Boolean accepted;
    private String messageStatus;
    private String remark;
}
