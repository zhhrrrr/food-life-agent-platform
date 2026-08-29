package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShopReviewListResponseDTO implements Serializable {

    private List<ShopReviewResponseDTO> reviews = new ArrayList<>();
    private Boolean hasMore;
    private Long lastId;
}
