package com.foodlife.business.trigger.http;

import com.foodlife.business.api.dto.SearchDocumentListResponseDTO;
import com.foodlife.business.api.dto.SearchDocumentResponseDTO;
import com.foodlife.business.domain.search.model.SearchDocumentEntity;
import com.foodlife.business.domain.search.service.SearchDocumentDomainService;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/internal/search")
public class SearchDocumentInternalController {

    private final SearchDocumentDomainService searchDocumentDomainService;

    public SearchDocumentInternalController(SearchDocumentDomainService searchDocumentDomainService) {
        this.searchDocumentDomainService = searchDocumentDomainService;
    }

    @GetMapping("/shops")
    public Response<SearchDocumentListResponseDTO> listShopDocuments(@RequestParam(required = false) Integer limit) {
        return Response.success(toResponse(searchDocumentDomainService.listShopDocuments(limit)));
    }

    @GetMapping("/packages")
    public Response<SearchDocumentListResponseDTO> listPackageDocuments(@RequestParam(required = false) Integer limit) {
        return Response.success(toResponse(searchDocumentDomainService.listPackageDocuments(limit)));
    }

    @GetMapping("/rules")
    public Response<SearchDocumentListResponseDTO> listRuleDocuments(@RequestParam(required = false) Integer limit) {
        return Response.success(toResponse(searchDocumentDomainService.listRuleDocuments(limit)));
    }

    private SearchDocumentListResponseDTO toResponse(List<SearchDocumentEntity> documents) {
        List<SearchDocumentResponseDTO> items = documents.stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());

        SearchDocumentListResponseDTO response = new SearchDocumentListResponseDTO();
        response.setCount(items.size());
        response.setDocuments(items);
        return response;
    }

    private SearchDocumentResponseDTO toDocumentResponse(SearchDocumentEntity source) {
        SearchDocumentResponseDTO target = new SearchDocumentResponseDTO();
        target.setId(source.getId());
        target.setTitle(source.getTitle());
        target.setContent(source.getContent());
        target.setSource(source.getSource());
        target.setCategory(source.getCategory());
        target.setMetadata(source.getMetadata());
        return target;
    }
}
