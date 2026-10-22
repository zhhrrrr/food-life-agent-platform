package com.foodlife.business.infrastructure.repository;

import com.foodlife.business.infrastructure.cache.BusinessCacheSupport;
import com.foodlife.business.infrastructure.dao.*;
import com.foodlife.business.infrastructure.dao.po.ShopReviewPO;
import com.foodlife.business.infrastructure.dao.po.BusinessConsumedMessagePO;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewStatsTest {
    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new Configuration(), "test");
        TableInfoHelper.initTableInfo(assistant, ShopReviewPO.class);
        TableInfoHelper.initTableInfo(assistant, BusinessConsumedMessagePO.class);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void hidingReviewRemovesOnlyItsOwnAlreadyAppliedContribution(boolean applied) {
        IShopReviewMapper reviews = mock(IShopReviewMapper.class);
        IShopMapper shops = mock(IShopMapper.class);
        IBusinessConsumedMessageMapper consumed = mock(IBusinessConsumedMessageMapper.class);
        ShopReviewPO review = new ShopReviewPO(); review.setId(1L); review.setUserId(7L);
        review.setShopId(9L); review.setReviewNo("REVIEW1"); review.setScore(2);
        review.setShopCommentsBefore(100); review.setShopScoreBefore(45);
        when(reviews.selectOne(any())).thenReturn(review);
        when(reviews.update(isNull(), any())).thenReturn(1);
        when(reviews.selectById(1L)).thenReturn(review);
        when(consumed.selectCount(any())).thenReturn(applied ? 1L : 0L);
        when(shops.decreaseReviewStats(9L, 2)).thenReturn(1);
        new ShopReviewRepository(reviews, shops, consumed, mock(BusinessCacheSupport.class)).hideReview(1L, 7L);
        if (applied) verify(shops).decreaseReviewStats(9L, 2);
        else verifyNoInteractions(shops);
    }
}
