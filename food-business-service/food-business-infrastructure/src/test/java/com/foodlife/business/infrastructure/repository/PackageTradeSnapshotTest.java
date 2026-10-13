package com.foodlife.business.infrastructure.repository;

import com.foodlife.business.domain.packagee.service.PackageDomainService;
import com.foodlife.business.domain.packagee.repository.IPackageRepository;
import com.foodlife.business.infrastructure.cache.BusinessCacheSupport;
import com.foodlife.business.infrastructure.dao.IMealPackageMapper;
import com.foodlife.business.infrastructure.dao.IPackageStockChangeRecordMapper;
import com.foodlife.business.infrastructure.dao.IShopMapper;
import com.foodlife.business.infrastructure.dao.po.MealPackagePO;
import com.foodlife.business.infrastructure.dao.po.ShopPO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PackageTradeSnapshotTest {
    @Test
    void batchReadsCurrentFactsInTwoQueriesAndPreservesRequestedOrder() {
        IMealPackageMapper packages = mock(IMealPackageMapper.class);
        IShopMapper shops = mock(IShopMapper.class);
        BusinessCacheSupport cache = mock(BusinessCacheSupport.class);
        PackageRepository repository = new PackageRepository(packages, shops,
                mock(IPackageStockChangeRecordMapper.class), cache);
        MealPackagePO first = mealPackage(1L, 12, 20);
        MealPackagePO second = mealPackage(2L, 4, 50);
        ShopPO shop = new ShopPO();
        shop.setId(10L);
        shop.setSold(70);
        when(packages.selectList(any())).thenReturn(List.of(first, second));
        when(shops.selectList(any())).thenReturn(List.of(shop));

        var result = repository.queryTradeSnapshots(List.of(2L, 1L, 2L, 99L));
        assertEquals(List.of(2L, 1L), result.stream().map(item -> item.getPackageId()).collect(Collectors.toList()));
        assertEquals(50, result.get(0).getSold());
        assertEquals(4, result.get(0).getStock());
        assertEquals(70, result.get(0).getShopSold());
        verify(packages, times(1)).selectList(any());
        verify(shops, times(1)).selectList(any());
        verifyNoInteractions(cache);

        second.setSold(51);
        second.setStock(3);
        shop.setSold(71);
        var refreshed = repository.queryTradeSnapshots(List.of(2L)).get(0);
        assertEquals(51, refreshed.getSold());
        assertEquals(3, refreshed.getStock());
        assertEquals(71, refreshed.getShopSold());
        verifyNoInteractions(cache);
    }

    @Test
    void singleReadBypassesOldSnapshotCache() {
        IMealPackageMapper packages = mock(IMealPackageMapper.class);
        IShopMapper shops = mock(IShopMapper.class);
        BusinessCacheSupport cache = mock(BusinessCacheSupport.class);
        PackageRepository repository = new PackageRepository(packages, shops,
                mock(IPackageStockChangeRecordMapper.class), cache);
        ShopPO shop = new ShopPO();
        shop.setId(10L);
        shop.setSold(8);
        when(packages.selectById(1L)).thenReturn(mealPackage(1L, 2, 8));
        when(shops.selectById(10L)).thenReturn(shop);
        assertEquals(8, repository.queryTradeSnapshot(1L).getSold());
        verifyNoInteractions(cache);
    }

    @Test
    void rejectsInvalidBatchBeforeDatabaseAccess() {
        IPackageRepository repository = mock(IPackageRepository.class);
        PackageDomainService service = new PackageDomainService(repository);
        assertThrows(IllegalArgumentException.class, () -> service.queryTradeSnapshots(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.queryTradeSnapshots(List.of(-1L)));
        assertThrows(IllegalArgumentException.class, () -> service.queryTradeSnapshots(java.util.Collections.nCopies(51, 1L)));
        verifyNoInteractions(repository);
    }

    @Test
    void salesConfirmationAndStockChangesStillReachBusinessRepository() {
        IPackageRepository repository = mock(IPackageRepository.class);
        PackageDomainService service = new PackageDomainService(repository);
        service.confirmPackageSold(1L, 2, "paid-100");
        service.rollbackPackageSold(1L, 2, "refund-100");
        service.occupyPackageStock(1L, 2, "create-100");
        service.releasePackageStock(1L, 2, "cancel-100");
        verify(repository).confirmPackageSold(1L, 2, "paid-100");
        verify(repository).rollbackPackageSold(1L, 2, "refund-100");
        verify(repository).occupyPackageStock(1L, 2, "create-100");
        verify(repository).releasePackageStock(1L, 2, "cancel-100");
        verifyNoMoreInteractions(repository);
    }

    private MealPackagePO mealPackage(Long id, int stock, int sold) {
        MealPackagePO item = new MealPackagePO();
        item.setId(id);
        item.setShopId(10L);
        item.setPrice(9800L);
        item.setStock(stock);
        item.setSold(sold);
        item.setStatus(1);
        return item;
    }
}
