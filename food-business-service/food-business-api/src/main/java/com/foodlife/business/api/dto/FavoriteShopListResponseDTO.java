package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class FavoriteShopListResponseDTO implements Serializable {

    private List<FavoriteShopResponseDTO> shops = new ArrayList<>();
    private Boolean hasMore;
    private Long lastId;
}
