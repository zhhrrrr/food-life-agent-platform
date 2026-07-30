package com.foodlife.business.domain.packagee.repository;

import com.foodlife.business.domain.packagee.model.MealPackageEntity;

import java.util.List;

public interface IPackageRepository {

    MealPackageEntity findById(Long id);

    List<MealPackageEntity> listByShopId(Long shopId);
}
