package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SearchDocumentListResponseDTO implements Serializable {

    private Integer count;
    private List<SearchDocumentResponseDTO> documents;
}
