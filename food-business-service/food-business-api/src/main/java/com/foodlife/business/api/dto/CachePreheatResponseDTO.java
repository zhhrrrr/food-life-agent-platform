package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CachePreheatResponseDTO implements Serializable {

    private Long shopId;
    private Long packageId;
    private Boolean shopPreheated;
    private Integer packageCount;
    private Boolean packageSnapshotPreheated;
}
