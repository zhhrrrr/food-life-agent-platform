package com.foodlife.business.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.business.api.dto.CreateShopReviewRequestDTO;
import com.foodlife.business.api.dto.ShopReviewListResponseDTO;
import com.foodlife.business.api.dto.ShopReviewResponseDTO;
import com.foodlife.business.domain.review.model.CreateShopReviewCommand;
import com.foodlife.business.domain.review.model.ShopReviewEntity;
import com.foodlife.business.domain.review.model.ShopReviewListResult;
import com.foodlife.business.domain.review.service.ShopReviewDomainService;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ShopReviewController {

    private final ShopReviewDomainService shopReviewDomainService;

    public ShopReviewController(ShopReviewDomainService shopReviewDomainService) {
        this.shopReviewDomainService = shopReviewDomainService;
    }

    @PostMapping("/reviews")
    public Response<ShopReviewResponseDTO> createReview(@RequestBody CreateShopReviewRequestDTO request) {
        try {
            ShopReviewEntity review = shopReviewDomainService.createReview(toCommand(request));
            return Response.success(toResponse(review));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/shop/{shopId}/reviews")
    public Response<ShopReviewListResponseDTO> listShopReviews(@PathVariable Long shopId,
                                                               @RequestParam(required = false) Long lastId,
                                                               @RequestParam(required = false) Integer pageSize) {
        try {
            return Response.success(toListResponse(shopReviewDomainService.listShopReviews(shopId, lastId, pageSize)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/reviews/my")
    public Response<ShopReviewListResponseDTO> listMyReviews(@RequestParam(required = false) Long lastId,
                                                             @RequestParam(required = false) Integer pageSize) {
        try {
            return Response.success(toListResponse(shopReviewDomainService.listMyReviews(UserHolder.getUserId(), lastId, pageSize)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private CreateShopReviewCommand toCommand(CreateShopReviewRequestDTO request) {
        CreateShopReviewCommand command = new CreateShopReviewCommand();
        command.setUserId(UserHolder.getUserId());
        command.setOrderId(request == null ? null : request.getOrderId());
        command.setScore(request == null ? null : request.getScore());
        command.setContent(request == null ? null : request.getContent());
        command.setImages(request == null ? null : request.getImages());
        return command;
    }

    private ShopReviewListResponseDTO toListResponse(ShopReviewListResult result) {
        ShopReviewListResponseDTO response = new ShopReviewListResponseDTO();
        response.setReviews(result.getReviews().stream().map(this::toResponse).collect(Collectors.toList()));
        response.setHasMore(result.getHasMore());
        response.setLastId(result.getLastId());
        return response;
    }

    private ShopReviewResponseDTO toResponse(ShopReviewEntity entity) {
        ShopReviewResponseDTO response = new ShopReviewResponseDTO();
        response.setReviewId(entity.getId());
        response.setReviewNo(entity.getReviewNo());
        response.setUserId(entity.getUserId());
        response.setShopId(entity.getShopId());
        response.setPackageId(entity.getPackageId());
        response.setOrderId(entity.getOrderId());
        response.setOrderNo(entity.getOrderNo());
        response.setScore(entity.getScore());
        response.setContent(entity.getContent());
        response.setImages(entity.getImages());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }
}
