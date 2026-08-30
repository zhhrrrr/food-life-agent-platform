package com.foodlife.business.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.business.api.dto.MealPackageResponseDTO;
import com.foodlife.business.api.dto.ShopHomepageResponseDTO;
import com.foodlife.business.api.dto.ShopInfoResponseDTO;
import com.foodlife.business.api.dto.ShopReviewResponseDTO;
import com.foodlife.business.api.dto.ShopReviewSummaryResponseDTO;
import com.foodlife.business.domain.homepage.model.ShopHomepageEntity;
import com.foodlife.business.domain.homepage.service.ShopHomepageDomainService;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.review.model.ShopReviewEntity;
import com.foodlife.business.domain.shop.model.ShopEntity;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shop-homepage")
public class ShopHomepageController {

    private final ShopHomepageDomainService shopHomepageDomainService;

    public ShopHomepageController(ShopHomepageDomainService shopHomepageDomainService) {
        this.shopHomepageDomainService = shopHomepageDomainService;
    }

    @GetMapping("/{shopId}")
    public Response<ShopHomepageResponseDTO> queryShopHomepage(@PathVariable Long shopId) {
        try {
            return Response.success(toResponse(shopHomepageDomainService.queryShopHomepage(UserHolder.getUserId(), shopId)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private ShopHomepageResponseDTO toResponse(ShopHomepageEntity entity) {
        ShopHomepageResponseDTO response = new ShopHomepageResponseDTO();
        response.setUserId(entity.getUserId());
        response.setShop(toShopResponse(entity.getShop()));
        response.setPackages(entity.getPackages().stream().map(this::toPackageResponse).collect(Collectors.toList()));
        response.setFavorite(entity.getFavorite());
        response.setReviewSummary(toReviewSummaryResponse(entity));
        return response;
    }

    private ShopInfoResponseDTO toShopResponse(ShopEntity entity) {
        ShopInfoResponseDTO response = new ShopInfoResponseDTO();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setCategoryId(entity.getCategoryId());
        response.setImages(entity.getImages());
        response.setArea(entity.getArea());
        response.setAddress(entity.getAddress());
        response.setLongitude(entity.getLongitude());
        response.setLatitude(entity.getLatitude());
        response.setAvgPrice(entity.getAvgPrice());
        response.setSold(entity.getSold());
        response.setComments(entity.getComments());
        response.setScore(entity.getScore());
        response.setOpenHours(entity.getOpenHours());
        return response;
    }

    private MealPackageResponseDTO toPackageResponse(MealPackageEntity entity) {
        MealPackageResponseDTO response = new MealPackageResponseDTO();
        response.setId(entity.getId());
        response.setShopId(entity.getShopId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setCoverImage(entity.getCoverImage());
        response.setPrice(entity.getPrice());
        response.setOriginalPrice(entity.getOriginalPrice());
        response.setStock(entity.getStock());
        response.setSold(entity.getSold());
        response.setStatus(entity.getStatus());
        response.setUseRule(entity.getUseRule());
        return response;
    }

    private ShopReviewSummaryResponseDTO toReviewSummaryResponse(ShopHomepageEntity entity) {
        ShopReviewSummaryResponseDTO response = new ShopReviewSummaryResponseDTO();
        response.setShopId(entity.getShop().getId());
        response.setComments(entity.getComments());
        response.setScore(entity.getScore());
        response.setLatestReviews(entity.getLatestReviews().stream().map(this::toReviewResponse).collect(Collectors.toList()));
        return response;
    }

    private ShopReviewResponseDTO toReviewResponse(ShopReviewEntity entity) {
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
