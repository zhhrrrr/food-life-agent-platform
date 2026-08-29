package com.foodlife.business.domain.review.port;

import com.foodlife.business.domain.review.model.TradeOrderForReviewEntity;

public interface ITradeOrderPort {

    TradeOrderForReviewEntity queryCurrentUserOrder(Long orderId);
}
