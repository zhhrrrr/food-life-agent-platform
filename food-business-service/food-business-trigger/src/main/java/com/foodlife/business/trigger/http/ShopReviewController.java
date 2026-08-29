package com.foodlife.business.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.business.api.dto.CreateShopReviewRequestDTO;
import com.foodlife.business.api.dto.DeleteShopReviewResponseDTO;
import com.foodlife.business.api.dto.ShopReviewListResponseDTO;
import com.foodlife.business.api.dto.ShopReviewResponseDTO;
import com.foodlife.business.api.dto.ShopReviewSummaryResponseDTO;
import com.foodlife.business.domain.review.model.CreateShopReviewCommand;
import com.foodlife.business.domain.review.model.ShopReviewEntity;
import com.foodlife.business.domain.review.model.ShopReviewListResult;
import com.foodlife.business.domain.review.service.ShopReviewDomainService;
import com.foodlife.business.domain.shop.model.ShopEntity;
import com.foodlife.business.domain.shop.service.ShopDomainService;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final ShopDomainService shopDomainService;

    public ShopReviewController(ShopReviewDomainService shopReviewDomainService, ShopDomainService shopDomainService) {
        this.shopReviewDomainService = shopReviewDomainService;
        this.shopDomainService = shopDomainService;
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

    @GetMapping("/shop/{shopId}/review-summary")
    public Response<ShopReviewSummaryResponseDTO> queryShopReviewSummary(@PathVariable Long shopId) {
        try {
            ShopEntity shop = shopDomainService.queryShopById(shopId);
            if (shop == null) {
                return Response.fail("404", "shop not found");
            }
            ShopReviewListResult latestReviews = shopReviewDomainService.listShopReviews(shopId, null, 3);
            ShopReviewSummaryResponseDTO response = new ShopReviewSummaryResponseDTO();
            response.setShopId(shop.getId());
            response.setComments(shop.getComments());
            response.setScore(shop.getScore());
            response.setLatestReviews(latestReviews.getReviews().stream().map(this::toResponse).collect(Collectors.toList()));
            return Response.success(response);
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/package/{packageId}/reviews")
    public Response<ShopReviewListResponseDTO> listPackageReviews(@PathVariable Long packageId,
                                                                  @RequestParam(required = false) Long lastId,
                                                                  @RequestParam(required = false) Integer pageSize) {
        try {
            return Response.success(toListResponse(shopReviewDomainService.listPackageReviews(packageId, lastId, pageSize)));
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

    @DeleteMapping("/reviews/{reviewId}")
    public Response<DeleteShopReviewResponseDTO> deleteMyReview(@PathVariable Long reviewId) {
        try {
            ShopReviewEntity review = shopReviewDomainService.hideMyReview(reviewId, UserHolder.getUserId());
            return Response.success(toDeleteResponse(review));
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

    private DeleteShopReviewResponseDTO toDeleteResponse(ShopReviewEntity entity) {
        DeleteShopReviewResponseDTO response = new DeleteShopReviewResponseDTO();
        response.setReviewId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setShopId(entity.getShopId());
        response.setPackageId(entity.getPackageId());
        response.setOrderId(entity.getOrderId());
        response.setReviewStatus(entity.getReviewStatus());
        response.setDeleted(entity.getReviewStatus() != null && entity.getReviewStatus() == 0);
        return response;
    }
}
