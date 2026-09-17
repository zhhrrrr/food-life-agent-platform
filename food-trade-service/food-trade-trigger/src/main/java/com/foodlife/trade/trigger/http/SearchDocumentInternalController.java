package com.foodlife.trade.trigger.http;

import com.foodlife.trade.api.dto.SearchDocumentListResponseDTO;
import com.foodlife.trade.api.dto.SearchDocumentResponseDTO;
import com.foodlife.trade.domain.order.search.model.SearchDocumentEntity;
import com.foodlife.trade.domain.order.search.service.SearchDocumentDomainService;
import com.foodlife.trade.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade/internal/search")
public class SearchDocumentInternalController {

    private final SearchDocumentDomainService searchDocumentDomainService;

    public SearchDocumentInternalController(SearchDocumentDomainService searchDocumentDomainService) {
        this.searchDocumentDomainService = searchDocumentDomainService;
    }

    @GetMapping("/activities")
    public Response<SearchDocumentListResponseDTO> listActivityDocuments(@RequestParam(required = false) Integer limit,
                                                                         @RequestParam(required = false) Integer offset) {
        return Response.success(toResponse(searchDocumentDomainService.listActivityDocuments(limit, offset)));
    }

    @GetMapping("/coupons")
    public Response<SearchDocumentListResponseDTO> listCouponDocuments(@RequestParam(required = false) Integer limit,
                                                                       @RequestParam(required = false) Integer offset) {
        return Response.success(toResponse(searchDocumentDomainService.listCouponDocuments(limit, offset)));
    }

    @GetMapping("/activities/group-buy/{activityId}")
    public Response<SearchDocumentResponseDTO> getGroupBuyActivityDocument(@PathVariable Long activityId) {
        return Response.success(toDocumentResponse(searchDocumentDomainService.findGroupBuyActivityDocument(activityId)));
    }

    @GetMapping("/activities/seckill/{activityId}")
    public Response<SearchDocumentResponseDTO> getSeckillActivityDocument(@PathVariable Long activityId) {
        return Response.success(toDocumentResponse(searchDocumentDomainService.findSeckillActivityDocument(activityId)));
    }

    @GetMapping("/coupons/{couponId}")
    public Response<SearchDocumentResponseDTO> getCouponDocument(@PathVariable Long couponId) {
        return Response.success(toDocumentResponse(searchDocumentDomainService.findCouponDocument(couponId)));
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
        if (source == null) {
            return null;
        }
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
