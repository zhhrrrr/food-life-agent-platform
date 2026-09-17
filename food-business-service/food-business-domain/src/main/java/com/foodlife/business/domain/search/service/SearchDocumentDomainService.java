package com.foodlife.business.domain.search.service;

import com.foodlife.business.domain.search.model.SearchDocumentEntity;
import com.foodlife.business.domain.search.repository.ISearchDocumentRepository;
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

    public List<SearchDocumentEntity> listShopDocuments(Integer limit, Integer offset) {
        return searchDocumentRepository.listShopDocuments(normalizeLimit(limit), normalizeOffset(offset));
    }

    public List<SearchDocumentEntity> listPackageDocuments(Integer limit, Integer offset) {
        return searchDocumentRepository.listPackageDocuments(normalizeLimit(limit), normalizeOffset(offset));
    }

    public List<SearchDocumentEntity> listReviewDocuments(Integer limit, Integer offset) {
        return searchDocumentRepository.listReviewDocuments(normalizeLimit(limit), normalizeOffset(offset));
    }

    public SearchDocumentEntity findShopDocument(Long shopId) {
        return searchDocumentRepository.findShopDocument(shopId);
    }

    public SearchDocumentEntity findPackageDocument(Long packageId) {
        return searchDocumentRepository.findPackageDocument(packageId);
    }

    public SearchDocumentEntity findReviewDocument(Long reviewId) {
        return searchDocumentRepository.findReviewDocument(reviewId);
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
