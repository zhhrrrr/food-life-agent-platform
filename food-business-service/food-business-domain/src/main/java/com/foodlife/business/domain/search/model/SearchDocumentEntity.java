package com.foodlife.business.domain.search.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class SearchDocumentEntity implements Serializable {

    private String id;
    private String title;
    private String content;
    private String source;
    private String category;
    private Map<String, Object> metadata;
}
