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

    public List<SearchDocumentEntity> listShopDocuments(Integer limit) {
        return searchDocumentRepository.listShopDocuments(normalizeLimit(limit));
    }

    public List<SearchDocumentEntity> listPackageDocuments(Integer limit) {
        return searchDocumentRepository.listPackageDocuments(normalizeLimit(limit));
    }

    private Integer normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
