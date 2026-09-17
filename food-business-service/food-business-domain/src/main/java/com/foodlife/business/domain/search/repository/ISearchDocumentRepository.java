package com.foodlife.business.domain.search.repository;

import com.foodlife.business.domain.search.model.SearchDocumentEntity;

import java.util.List;

public interface ISearchDocumentRepository {

    List<SearchDocumentEntity> listShopDocuments(Integer limit, Integer offset);

    List<SearchDocumentEntity> listPackageDocuments(Integer limit, Integer offset);

    List<SearchDocumentEntity> listReviewDocuments(Integer limit, Integer offset);

    SearchDocumentEntity findShopDocument(Long shopId);

    SearchDocumentEntity findPackageDocument(Long packageId);

    SearchDocumentEntity findReviewDocument(Long reviewId);
}
