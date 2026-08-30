package com.foodlife.business.domain.homepage.service;

import com.foodlife.business.domain.favorite.service.ShopFavoriteDomainService;
import com.foodlife.business.domain.homepage.model.ShopHomepageEntity;
import com.foodlife.business.domain.packagee.service.PackageDomainService;
import com.foodlife.business.domain.review.model.ShopReviewListResult;
import com.foodlife.business.domain.review.service.ShopReviewDomainService;
import com.foodlife.business.domain.shop.model.ShopEntity;
import com.foodlife.business.domain.shop.service.ShopDomainService;
import org.springframework.stereotype.Service;

@Service
public class ShopHomepageDomainService {

    private static final int LATEST_REVIEW_SIZE = 3;

    private final ShopDomainService shopDomainService;
    private final PackageDomainService packageDomainService;
    private final ShopReviewDomainService shopReviewDomainService;
    private final ShopFavoriteDomainService shopFavoriteDomainService;

    public ShopHomepageDomainService(ShopDomainService shopDomainService,
                                     PackageDomainService packageDomainService,
                                     ShopReviewDomainService shopReviewDomainService,
                                     ShopFavoriteDomainService shopFavoriteDomainService) {
        this.shopDomainService = shopDomainService;
        this.packageDomainService = packageDomainService;
        this.shopReviewDomainService = shopReviewDomainService;
        this.shopFavoriteDomainService = shopFavoriteDomainService;
    }

    public ShopHomepageEntity queryShopHomepage(Long userId, Long shopId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (shopId == null) {
            throw new IllegalArgumentException("shopId required");
        }

        ShopEntity shop = shopDomainService.queryShopById(shopId);
        if (shop == null) {
            throw new IllegalArgumentException("shop not found");
        }

        ShopReviewListResult latestReviews = shopReviewDomainService.listShopReviews(shopId, null, LATEST_REVIEW_SIZE);

        ShopHomepageEntity homepage = new ShopHomepageEntity();
        homepage.setUserId(userId);
        homepage.setShop(shop);
        homepage.setPackages(packageDomainService.queryPackagesByShopId(shopId));
        homepage.setFavorite(shopFavoriteDomainService.isFavorite(userId, shopId));
        homepage.setComments(shop.getComments());
        homepage.setScore(shop.getScore());
        homepage.setLatestReviews(latestReviews.getReviews());
        return homepage;
    }
}
