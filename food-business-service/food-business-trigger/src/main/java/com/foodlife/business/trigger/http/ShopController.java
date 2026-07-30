package com.foodlife.business.trigger.http;

import com.foodlife.business.domain.shop.model.ShopCategoryEntity;
import com.foodlife.business.domain.shop.model.ShopEntity;
import com.foodlife.business.domain.shop.service.ShopDomainService;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ShopController {

    private final ShopDomainService shopDomainService;

    public ShopController(ShopDomainService shopDomainService) {
        this.shopDomainService = shopDomainService;
    }

    @GetMapping("/shop-category/list")
    public Response<List<ShopCategoryEntity>> listCategories() {
        return Response.success(shopDomainService.listCategories());
    }

    @GetMapping("/shop/{id}")
    public Response<ShopEntity> queryShopById(@PathVariable Long id) {
        return Response.success(shopDomainService.queryShopById(id));
    }

    @GetMapping("/shop/of/category")
    public Response<List<ShopEntity>> queryShopByCategory(@RequestParam Long categoryId,
                                                          @RequestParam(defaultValue = "1") Integer current) {
        return Response.success(shopDomainService.queryShopsByCategory(categoryId, current));
    }

    @GetMapping("/shop/of/name")
    public Response<List<ShopEntity>> queryShopByName(@RequestParam(required = false) String name,
                                                      @RequestParam(defaultValue = "1") Integer current) {
        return Response.success(shopDomainService.queryShopsByName(name, current));
    }
}
