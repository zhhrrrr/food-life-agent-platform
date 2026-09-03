package com.foodlife.trade.infrastructure.port;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.foodlife.business.api.dto.PackageStockChangeRecordResponseDTO;
import com.foodlife.business.api.dto.PackageStockChangeResponseDTO;
import com.foodlife.business.api.dto.PackageTradeSnapshotResponseDTO;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.normal.model.PackageStockChangeRecord;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.infrastructure.feign.BusinessPackageClient;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BusinessPackagePort implements IBusinessPackagePort {

    private static final String BUSINESS_PACKAGE_SNAPSHOT_RESOURCE = "trade.feign.business.package.snapshot";
    private static final String BUSINESS_PACKAGE_STOCK_RESOURCE = "trade.feign.business.package.stock";

    private final BusinessPackageClient businessPackageClient;

    public BusinessPackagePort(BusinessPackageClient businessPackageClient) {
        this.businessPackageClient = businessPackageClient;
    }

    @Override
    public PackageTradeSnapshot queryTradeSnapshot(Long packageId) {
        Entry entry = null;
        try {
            entry = SphU.entry(BUSINESS_PACKAGE_SNAPSHOT_RESOURCE, EntryType.OUT, 1, packageId);
            com.foodlife.business.types.response.Response<PackageTradeSnapshotResponseDTO> response =
                    businessPackageClient.queryTradeSnapshot(packageId);
            if (response != null && "503".equals(response.getCode())) {
                throw new IllegalStateException(response.getMessage());
            }
            if (response == null || !"0000".equals(response.getCode()) || response.getData() == null) {
                return null;
            }
            return toSnapshot(response.getData());
        } catch (BlockException e) {
            throw new IllegalStateException("service busy, please try again later");
        } catch (FeignException e) {
            Tracer.trace(e);
            throw new IllegalStateException("service busy, please try again later");
        } catch (IllegalStateException e) {
            Tracer.trace(e);
            throw e;
        } finally {
            if (entry != null) {
                entry.exit(1, packageId);
            }
        }
    }

    @Override
    public List<PackageStockChangeRecord> listStockChangeRecords(String operationIdPrefix, Long packageId, Integer limit) {
        com.foodlife.business.types.response.Response<List<PackageStockChangeRecordResponseDTO>> response;
        try {
            response = businessPackageClient.listStockChangeRecords(
                    trimToNull(operationIdPrefix),
                    packageId,
                    limit == null ? 20 : limit
            );
        } catch (FeignException e) {
            return new ArrayList<>();
        }
        if (response == null || !"0000".equals(response.getCode()) || response.getData() == null) {
            return new ArrayList<>();
        }
        return toStockChangeRecords(response.getData());
    }

    @Override
    public void occupyPackageStock(Long packageId, Integer quantity) {
        occupyPackageStock(packageId, quantity, null);
    }

    @Override
    public void occupyPackageStock(Long packageId, Integer quantity, String operationId) {
        postPackageStockAction(packageId, quantity, operationId, "/stock/occupy");
    }

    @Override
    public void releasePackageStock(Long packageId, Integer quantity) {
        releasePackageStock(packageId, quantity, null);
    }

    @Override
    public void releasePackageStock(Long packageId, Integer quantity, String operationId) {
        postPackageStockAction(packageId, quantity, operationId, "/stock/release");
    }

    @Override
    public void confirmPackageSold(Long packageId, Integer quantity) {
        confirmPackageSold(packageId, quantity, null);
    }

    @Override
    public void confirmPackageSold(Long packageId, Integer quantity, String operationId) {
        postPackageStockAction(packageId, quantity, operationId, "/sold/confirm");
    }

    @Override
    public void rollbackPackageSold(Long packageId, Integer quantity) {
        rollbackPackageSold(packageId, quantity, null);
    }

    @Override
    public void rollbackPackageSold(Long packageId, Integer quantity, String operationId) {
        postPackageStockAction(packageId, quantity, operationId, "/sold/rollback");
    }

    private void postPackageStockAction(Long packageId, Integer quantity, String operationId, String actionPath) {
        Entry entry = null;
        try {
            entry = SphU.entry(BUSINESS_PACKAGE_STOCK_RESOURCE, EntryType.OUT, 1, packageId);
            com.foodlife.business.types.response.Response<PackageStockChangeResponseDTO> response =
                    doPostPackageStockAction(packageId, quantity, trimToNull(operationId), actionPath);
            if (response != null && "503".equals(response.getCode())) {
                throw new IllegalStateException(response.getMessage());
            }
            if (response == null || !"0000".equals(response.getCode())) {
                throw new IllegalStateException(response == null ? "package stock action failed" : response.getMessage());
            }
        } catch (BlockException e) {
            throw new IllegalStateException("package stock service busy, please try again later");
        } catch (FeignException e) {
            Tracer.trace(e);
            throw new IllegalStateException("package stock action failed");
        } catch (IllegalStateException e) {
            Tracer.trace(e);
            throw e;
        } finally {
            if (entry != null) {
                entry.exit(1, packageId);
            }
        }
    }

    private com.foodlife.business.types.response.Response<PackageStockChangeResponseDTO> doPostPackageStockAction(Long packageId,
                                                                                                                 Integer quantity,
                                                                                                                 String operationId,
                                                                                                                 String actionPath) {
        if ("/stock/occupy".equals(actionPath)) {
            return businessPackageClient.occupyPackageStock(packageId, quantity, operationId);
        }
        if ("/stock/release".equals(actionPath)) {
            return businessPackageClient.releasePackageStock(packageId, quantity, operationId);
        }
        if ("/sold/confirm".equals(actionPath)) {
            return businessPackageClient.confirmPackageSold(packageId, quantity, operationId);
        }
        if ("/sold/rollback".equals(actionPath)) {
            return businessPackageClient.rollbackPackageSold(packageId, quantity, operationId);
        }
        throw new IllegalArgumentException("unsupported package stock action");
    }

    private PackageTradeSnapshot toSnapshot(PackageTradeSnapshotResponseDTO data) {
        PackageTradeSnapshot snapshot = new PackageTradeSnapshot();
        snapshot.setShopId(data.getShopId());
        snapshot.setShopName(data.getShopName());
        snapshot.setPackageId(data.getPackageId());
        snapshot.setPackageName(data.getPackageName());
        snapshot.setPackageDescription(data.getPackageDescription());
        snapshot.setCoverImage(data.getCoverImage());
        snapshot.setPrice(data.getPrice());
        snapshot.setOriginalPrice(data.getOriginalPrice());
        snapshot.setStock(data.getStock());
        snapshot.setPackageStatus(data.getPackageStatus());
        snapshot.setUseRule(data.getUseRule());
        return snapshot;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<PackageStockChangeRecord> toStockChangeRecords(List<PackageStockChangeRecordResponseDTO> records) {
        List<PackageStockChangeRecord> result = new ArrayList<>();
        for (PackageStockChangeRecordResponseDTO record : records) {
            result.add(toStockChangeRecord(record));
        }
        return result;
    }

    private PackageStockChangeRecord toStockChangeRecord(PackageStockChangeRecordResponseDTO source) {
        PackageStockChangeRecord record = new PackageStockChangeRecord();
        record.setId(source.getId());
        record.setOperationId(source.getOperationId());
        record.setPackageId(source.getPackageId());
        record.setQuantity(source.getQuantity());
        record.setChangeType(source.getChangeType());
        record.setChangeStatus(source.getChangeStatus());
        record.setCreateTime(source.getCreateTime());
        record.setUpdateTime(source.getUpdateTime());
        return record;
    }
}
