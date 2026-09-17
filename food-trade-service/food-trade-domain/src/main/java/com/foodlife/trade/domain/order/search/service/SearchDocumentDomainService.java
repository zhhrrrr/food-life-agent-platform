package com.foodlife.trade.domain.order.search.service;

import com.foodlife.trade.domain.order.search.model.SearchDocumentEntity;
import com.foodlife.trade.domain.order.search.repository.ISearchDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchDocumentDomainService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    private final ISearchDocumentRepository searchDocumentRepository;

    public SearchDocumentDomainService(ISearchDocumentRepository searchDocumentRepository) {
        this.searchDocumentRepository = searchDocumentRepository;
    }

    public List<SearchDocumentEntity> listActivityDocuments(Integer limit, Integer offset) {
        return searchDocumentRepository.listActivityDocuments(normalizeLimit(limit), normalizeOffset(offset));
    }

    public List<SearchDocumentEntity> listCouponDocuments(Integer limit, Integer offset) {
        return searchDocumentRepository.listCouponDocuments(normalizeLimit(limit), normalizeOffset(offset));
    }

    public SearchDocumentEntity findGroupBuyActivityDocument(Long activityId) {
        return searchDocumentRepository.findGroupBuyActivityDocument(activityId);
    }

    public SearchDocumentEntity findSeckillActivityDocument(Long activityId) {
        return searchDocumentRepository.findSeckillActivityDocument(activityId);
    }

    public SearchDocumentEntity findCouponDocument(Long couponId) {
        return searchDocumentRepository.findCouponDocument(couponId);
    }

    private Integer normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Integer normalizeOffset(Integer offset) {
        if (offset == null || offset <= 0) {
            return 0;
        }
        return offset;
    }
}
