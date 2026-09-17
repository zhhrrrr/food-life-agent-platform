package com.foodlife.business.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodlife.business.domain.search.model.SearchDocumentEntity;
import com.foodlife.business.domain.search.repository.ISearchDocumentRepository;
import com.foodlife.business.infrastructure.dao.IMealPackageMapper;
import com.foodlife.business.infrastructure.dao.IShopCategoryMapper;
import com.foodlife.business.infrastructure.dao.IShopMapper;
import com.foodlife.business.infrastructure.dao.IShopReviewMapper;
import com.foodlife.business.infrastructure.dao.po.MealPackagePO;
import com.foodlife.business.infrastructure.dao.po.ShopCategoryPO;
import com.foodlife.business.infrastructure.dao.po.ShopPO;
import com.foodlife.business.infrastructure.dao.po.ShopReviewPO;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class SearchDocumentRepository implements ISearchDocumentRepository {

    private static final String SHOP_SOURCE = "food_shop_index";
    private static final String PACKAGE_SOURCE = "food_package_index";
    private static final String REVIEW_SOURCE = "food_review_index";

    private final IShopMapper shopMapper;
    private final IShopCategoryMapper shopCategoryMapper;
    private final IMealPackageMapper mealPackageMapper;
    private final IShopReviewMapper shopReviewMapper;

    public SearchDocumentRepository(IShopMapper shopMapper,
                                    IShopCategoryMapper shopCategoryMapper,
                                    IMealPackageMapper mealPackageMapper,
                                    IShopReviewMapper shopReviewMapper) {
        this.shopMapper = shopMapper;
        this.shopCategoryMapper = shopCategoryMapper;
        this.mealPackageMapper = mealPackageMapper;
        this.shopReviewMapper = shopReviewMapper;
    }

    @Override
    public List<SearchDocumentEntity> listShopDocuments(Integer limit) {
        Map<Long, ShopCategoryPO> categoryMap = listCategoryMap();
        return shopMapper.selectList(new LambdaQueryWrapper<ShopPO>()
                        .eq(ShopPO::getStatus, 1)
                        .orderByDesc(ShopPO::getSold)
                        .last("limit " + limit))
                .stream()
                .map(shop -> toShopDocument(shop, categoryMap.get(shop.getCategoryId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchDocumentEntity> listPackageDocuments(Integer limit) {
        Map<Long, ShopPO> shopMap = listShopMap();
        return mealPackageMapper.selectList(new LambdaQueryWrapper<MealPackagePO>()
                        .eq(MealPackagePO::getStatus, 1)
                        .orderByDesc(MealPackagePO::getSold)
                        .last("limit " + limit))
                .stream()
                .map(mealPackage -> toPackageDocument(mealPackage, shopMap.get(mealPackage.getShopId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchDocumentEntity> listReviewDocuments(Integer limit) {
        Map<Long, ShopPO> shopMap = listShopMap();
        Map<Long, MealPackagePO> packageMap = listPackageMap();
        return shopReviewMapper.selectList(new LambdaQueryWrapper<ShopReviewPO>()
                        .eq(ShopReviewPO::getReviewStatus, 1)
                        .orderByDesc(ShopReviewPO::getId)
                        .last("limit " + limit))
                .stream()
                .map(review -> toReviewDocument(
                        review,
                        shopMap.get(review.getShopId()),
                        packageMap.get(review.getPackageId())
                ))
                .collect(Collectors.toList());
    }

    @Override
    public SearchDocumentEntity findShopDocument(Long shopId) {
        ShopPO shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
            return null;
        }
        ShopCategoryPO category = shopCategoryMapper.selectById(shop.getCategoryId());
        return toShopDocument(shop, category);
    }

    @Override
    public SearchDocumentEntity findPackageDocument(Long packageId) {
        MealPackagePO mealPackage = mealPackageMapper.selectById(packageId);
        if (mealPackage == null || mealPackage.getStatus() == null || mealPackage.getStatus() != 1) {
            return null;
        }
        ShopPO shop = shopMapper.selectById(mealPackage.getShopId());
        if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
            return null;
        }
        return toPackageDocument(mealPackage, shop);
    }

    @Override
    public SearchDocumentEntity findReviewDocument(Long reviewId) {
        ShopReviewPO review = shopReviewMapper.selectById(reviewId);
        if (review == null || review.getReviewStatus() == null || review.getReviewStatus() != 1) {
            return null;
        }
        ShopPO shop = shopMapper.selectById(review.getShopId());
        MealPackagePO mealPackage = mealPackageMapper.selectById(review.getPackageId());
        return toReviewDocument(review, shop, mealPackage);
    }

    private Map<Long, ShopCategoryPO> listCategoryMap() {
        return shopCategoryMapper.selectList(new LambdaQueryWrapper<ShopCategoryPO>())
                .stream()
                .collect(Collectors.toMap(ShopCategoryPO::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, ShopPO> listShopMap() {
        List<ShopPO> shops = shopMapper.selectList(new LambdaQueryWrapper<ShopPO>()
                .eq(ShopPO::getStatus, 1));
        if (shops == null || shops.isEmpty()) {
            return Collections.emptyMap();
        }
        return shops.stream()
                .collect(Collectors.toMap(ShopPO::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, MealPackagePO> listPackageMap() {
        List<MealPackagePO> packages = mealPackageMapper.selectList(new LambdaQueryWrapper<MealPackagePO>()
                .eq(MealPackagePO::getStatus, 1));
        if (packages == null || packages.isEmpty()) {
            return Collections.emptyMap();
        }
        return packages.stream()
                .collect(Collectors.toMap(MealPackagePO::getId, Function.identity(), (left, right) -> left));
    }

    private SearchDocumentEntity toShopDocument(ShopPO shop, ShopCategoryPO category) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("shopId", shop.getId());
        metadata.put("shopName", shop.getName());
        metadata.put("categoryId", shop.getCategoryId());
        metadata.put("categoryName", category == null ? null : category.getName());
        metadata.put("area", shop.getArea());
        metadata.put("address", shop.getAddress());
        metadata.put("avgPrice", shop.getAvgPrice());
        metadata.put("sold", shop.getSold());
        metadata.put("comments", shop.getComments());
        metadata.put("score", shop.getScore());
        metadata.put("openHours", shop.getOpenHours());

        SearchDocumentEntity document = new SearchDocumentEntity();
        document.setId("shop-" + shop.getId());
        document.setTitle(shop.getName());
        document.setContent(joinText(
                shop.getName(),
                category == null ? null : category.getName(),
                shop.getArea(),
                shop.getAddress(),
                shop.getOpenHours()
        ));
        document.setSource(SHOP_SOURCE);
        document.setCategory(category == null ? null : category.getName());
        document.setMetadata(metadata);
        return document;
    }

    private SearchDocumentEntity toPackageDocument(MealPackagePO mealPackage, ShopPO shop) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("packageId", mealPackage.getId());
        metadata.put("packageName", mealPackage.getName());
        metadata.put("shopId", mealPackage.getShopId());
        metadata.put("shopName", shop == null ? null : shop.getName());
        metadata.put("price", mealPackage.getPrice());
        metadata.put("originalPrice", mealPackage.getOriginalPrice());
        metadata.put("stock", mealPackage.getStock());
        metadata.put("sold", mealPackage.getSold());
        metadata.put("status", mealPackage.getStatus());
        metadata.put("useRule", mealPackage.getUseRule());

        SearchDocumentEntity document = new SearchDocumentEntity();
        document.setId("package-" + mealPackage.getId());
        document.setTitle(mealPackage.getName());
        document.setContent(joinText(
                mealPackage.getName(),
                mealPackage.getDescription(),
                mealPackage.getUseRule(),
                shop == null ? null : shop.getName()
        ));
        document.setSource(PACKAGE_SOURCE);
        document.setCategory("meal_package");
        document.setMetadata(metadata);
        return document;
    }

    private SearchDocumentEntity toReviewDocument(ShopReviewPO review, ShopPO shop, MealPackagePO mealPackage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reviewId", review.getId());
        metadata.put("reviewNo", review.getReviewNo());
        metadata.put("shopId", review.getShopId());
        metadata.put("shopName", shop == null ? null : shop.getName());
        metadata.put("packageId", review.getPackageId());
        metadata.put("packageName", mealPackage == null ? null : mealPackage.getName());
        metadata.put("score", review.getScore());
        metadata.put("images", review.getImages());
        metadata.put("createTime", review.getCreateTime());

        SearchDocumentEntity document = new SearchDocumentEntity();
        document.setId("review-" + review.getId());
        document.setTitle(joinText(
                shop == null ? null : shop.getName(),
                mealPackage == null ? null : mealPackage.getName(),
                "review"
        ));
        document.setContent(joinText(
                review.getContent(),
                shop == null ? null : shop.getName(),
                mealPackage == null ? null : mealPackage.getName()
        ));
        document.setSource(REVIEW_SOURCE);
        document.setCategory("shop_review");
        document.setMetadata(metadata);
        return document;
    }

    private String joinText(String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }
}
