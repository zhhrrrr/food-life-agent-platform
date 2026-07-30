package com.foodlife.business.domain.packagee.service;

import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.repository.IPackageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PackageDomainService {

    private final IPackageRepository packageRepository;

    public PackageDomainService(IPackageRepository packageRepository) {
        this.packageRepository = packageRepository;
    }

    public MealPackageEntity queryPackageById(Long id) {
        return packageRepository.findById(id);
    }

    public List<MealPackageEntity> queryPackagesByShopId(Long shopId) {
        return packageRepository.listByShopId(shopId);
    }
}
