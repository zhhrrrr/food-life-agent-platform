package com.foodlife.business.domain.search.repository;

import com.foodlife.business.domain.search.model.SearchDocumentEntity;

import java.util.List;

public interface ISearchDocumentRepository {

    List<SearchDocumentEntity> listShopDocuments(Integer limit);

    List<SearchDocumentEntity> listPackageDocuments(Integer limit);

    List<SearchDocumentEntity> listReviewDocuments(Integer limit);
}
