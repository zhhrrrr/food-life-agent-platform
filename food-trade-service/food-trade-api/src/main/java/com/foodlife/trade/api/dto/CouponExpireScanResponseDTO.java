package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CouponExpireScanResponseDTO implements Serializable {

    private LocalDateTime scanTime;
    private LocalDateTime expireBefore;
    private Integer expiredCount;
    private Integer limit;
}
