package com.foodlife.business.domain.review.service;

import com.foodlife.business.domain.review.model.CreateShopReviewCommand;
import com.foodlife.business.domain.review.model.ShopReviewEntity;
import com.foodlife.business.domain.review.model.ShopReviewListResult;
import com.foodlife.business.domain.review.model.TradeOrderForReviewEntity;
import com.foodlife.business.domain.review.port.ITradeOrderPort;
import com.foodlife.business.domain.review.repository.IShopReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ShopReviewDomainService {

    private static final String REVIEWABLE_ORDER_STATUS = "USED";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_CONTENT_LENGTH = 500;

    private final IShopReviewRepository shopReviewRepository;
    private final ITradeOrderPort tradeOrderPort;

    public ShopReviewDomainService(IShopReviewRepository shopReviewRepository, ITradeOrderPort tradeOrderPort) {
        this.shopReviewRepository = shopReviewRepository;
        this.tradeOrderPort = tradeOrderPort;
    }

    public ShopReviewEntity createReview(CreateShopReviewCommand command) {
        checkCreateCommand(command);
        TradeOrderForReviewEntity order = tradeOrderPort.queryCurrentUserOrder(command.getOrderId());
        checkOrderReviewable(command, order);
        if (shopReviewRepository.existsByOrderIdAndUserId(command.getOrderId(), command.getUserId())) {
            throw new IllegalArgumentException("order already reviewed");
        }

        ShopReviewEntity review = new ShopReviewEntity();
        review.setReviewNo(generateReviewNo(command.getUserId()));
        review.setUserId(command.getUserId());
        review.setShopId(order.getShopId());
        review.setPackageId(order.getPackageId());
        review.setOrderId(order.getOrderId());
        review.setOrderNo(order.getOrderNo());
        review.setScore(command.getScore());
        review.setContent(command.getContent().trim());
        review.setImages(trimToEmpty(command.getImages()));
        review.setReviewStatus(1);
        return shopReviewRepository.saveReview(review);
    }

    public ShopReviewListResult listShopReviews(Long shopId, Long lastId, Integer pageSize) {
        if (shopId == null) {
            throw new IllegalArgumentException("shopId required");
        }
        return buildListResult(shopReviewRepository.listByShopId(shopId, lastId, normalizePageSize(pageSize) + 1),
                normalizePageSize(pageSize));
    }

    public ShopReviewListResult listMyReviews(Long userId, Long lastId, Integer pageSize) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        return buildListResult(shopReviewRepository.listByUserId(userId, lastId, normalizePageSize(pageSize) + 1),
                normalizePageSize(pageSize));
    }

    private void checkCreateCommand(CreateShopReviewCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("review command required");
        }
        if (command.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (command.getOrderId() == null) {
            throw new IllegalArgumentException("orderId required");
        }
        if (command.getScore() == null || command.getScore() < 1 || command.getScore() > 5) {
            throw new IllegalArgumentException("score must be between 1 and 5");
        }
        if (!StringUtils.hasText(command.getContent())) {
            throw new IllegalArgumentException("content required");
        }
        if (command.getContent().trim().length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content length must be less than 500");
        }
    }

    private void checkOrderReviewable(CreateShopReviewCommand command, TradeOrderForReviewEntity order) {
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (!command.getUserId().equals(order.getUserId())) {
            throw new IllegalArgumentException("order user not match");
        }
        if (!REVIEWABLE_ORDER_STATUS.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order not used");
        }
        if (order.getShopId() == null || order.getPackageId() == null) {
            throw new IllegalArgumentException("order package snapshot missing");
        }
    }

    private ShopReviewListResult buildListResult(List<ShopReviewEntity> source, int pageSize) {
        ShopReviewListResult result = new ShopReviewListResult();
        boolean hasMore = source.size() > pageSize;
        if (hasMore) {
            source = source.subList(0, pageSize);
        }
        result.setReviews(source);
        result.setHasMore(hasMore);
        result.setLastId(source.isEmpty() ? null : source.get(source.size() - 1).getId());
        return result;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String generateReviewNo(Long userId) {
        return "RV" + System.currentTimeMillis() + userId;
    }
}
