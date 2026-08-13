package com.foodlife.trade.infrastructure.port;

import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.types.response.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class BusinessPackagePort implements IBusinessPackagePort {

    private final RestTemplate restTemplate;
    private final String businessServiceBaseUrl;

    public BusinessPackagePort(RestTemplate restTemplate,
                               @Value("${food.business-service.base-url}") String businessServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.businessServiceBaseUrl = businessServiceBaseUrl;
    }

    @Override
    public PackageTradeSnapshot queryTradeSnapshot(Long packageId) {
        Response response = restTemplate.getForObject(
                businessServiceBaseUrl + "/api/package/trade-snapshot/" + packageId,
                Response.class
        );
        if (response == null || !"0000".equals(response.getCode()) || response.getData() == null) {
            return null;
        }
        return toSnapshot(response.getData());
    }

    @Override
    public void occupyPackageStock(Long packageId, Integer quantity) {
        postPackageStockAction(packageId, quantity, "/stock/occupy");
    }

    @Override
    public void releasePackageStock(Long packageId, Integer quantity) {
        postPackageStockAction(packageId, quantity, "/stock/release");
    }

    @Override
    public void confirmPackageSold(Long packageId, Integer quantity) {
        postPackageStockAction(packageId, quantity, "/sold/confirm");
    }

    @Override
    public void rollbackPackageSold(Long packageId, Integer quantity) {
        postPackageStockAction(packageId, quantity, "/sold/rollback");
    }

    private void postPackageStockAction(Long packageId, Integer quantity, String actionPath) {
        Response response = restTemplate.postForObject(
                businessServiceBaseUrl + "/api/package/" + packageId + actionPath + "?quantity=" + quantity,
                null,
                Response.class
        );
        if (response == null || !"0000".equals(response.getCode())) {
            throw new IllegalStateException(response == null ? "package stock action failed" : response.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private PackageTradeSnapshot toSnapshot(Object data) {
        Map<String, Object> map = (Map<String, Object>) data;
        PackageTradeSnapshot snapshot = new PackageTradeSnapshot();
        snapshot.setShopId(toLong(map.get("shopId")));
        snapshot.setShopName((String) map.get("shopName"));
        snapshot.setPackageId(toLong(map.get("packageId")));
        snapshot.setPackageName((String) map.get("packageName"));
        snapshot.setPackageDescription((String) map.get("packageDescription"));
        snapshot.setCoverImage((String) map.get("coverImage"));
        snapshot.setPrice(toLong(map.get("price")));
        snapshot.setOriginalPrice(toLong(map.get("originalPrice")));
        snapshot.setStock(toInteger(map.get("stock")));
        snapshot.setPackageStatus(toInteger(map.get("packageStatus")));
        snapshot.setUseRule((String) map.get("useRule"));
        return snapshot;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
