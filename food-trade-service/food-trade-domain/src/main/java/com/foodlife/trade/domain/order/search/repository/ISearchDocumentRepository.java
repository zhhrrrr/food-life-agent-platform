package com.foodlife.trade.domain.order.search.repository;

import com.foodlife.trade.domain.order.search.model.SearchDocumentEntity;

import java.util.List;

public interface ISearchDocumentRepository {

    List<SearchDocumentEntity> listActivityDocuments(Integer limit);

    List<SearchDocumentEntity> listCouponDocuments(Integer limit);

    SearchDocumentEntity findGroupBuyActivityDocument(Long activityId);

    SearchDocumentEntity findSeckillActivityDocument(Long activityId);

    SearchDocumentEntity findCouponDocument(Long couponId);
}
