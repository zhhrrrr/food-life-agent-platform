package com.foodlife.business.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.business.api.dto.FavoriteShopListResponseDTO;
import com.foodlife.business.api.dto.FavoriteShopResponseDTO;
import com.foodlife.business.api.dto.ShopFavoriteResponseDTO;
import com.foodlife.business.api.dto.ShopFavoriteStatusResponseDTO;
import com.foodlife.business.domain.favorite.model.FavoriteShopEntity;
import com.foodlife.business.domain.favorite.model.FavoriteShopListResult;
import com.foodlife.business.domain.favorite.model.ShopFavoriteCommand;
import com.foodlife.business.domain.favorite.model.ShopFavoriteEntity;
import com.foodlife.business.domain.favorite.service.ShopFavoriteDomainService;
import com.foodlife.business.types.response.Response;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ShopFavoriteController {

    private final ShopFavoriteDomainService shopFavoriteDomainService;

    public ShopFavoriteController(ShopFavoriteDomainService shopFavoriteDomainService) {
        this.shopFavoriteDomainService = shopFavoriteDomainService;
    }

    @PostMapping("/favorites/shops/{shopId}")
    public Response<ShopFavoriteResponseDTO> favoriteShop(@PathVariable Long shopId) {
        try {
            return Response.success(toResponse(shopFavoriteDomainService.favoriteShop(toCommand(shopId))));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @DeleteMapping("/favorites/shops/{shopId}")
    public Response<ShopFavoriteResponseDTO> unfavoriteShop(@PathVariable Long shopId) {
        try {
            return Response.success(toResponse(shopFavoriteDomainService.unfavoriteShop(toCommand(shopId))));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/favorites/shops/{shopId}/status")
    public Response<ShopFavoriteStatusResponseDTO> queryFavoriteStatus(@PathVariable Long shopId) {
        try {
            ShopFavoriteStatusResponseDTO response = new ShopFavoriteStatusResponseDTO();
            response.setUserId(UserHolder.getUserId());
            response.setShopId(shopId);
            response.setFavorite(shopFavoriteDomainService.isFavorite(UserHolder.getUserId(), shopId));
            return Response.success(response);
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    @GetMapping("/favorites/shops")
    public Response<FavoriteShopListResponseDTO> listFavoriteShops(@RequestParam(required = false) Long lastId,
                                                                   @RequestParam(required = false) Integer pageSize) {
        try {
            return Response.success(toListResponse(shopFavoriteDomainService.listFavoriteShops(UserHolder.getUserId(), lastId, pageSize)));
        } catch (IllegalArgumentException e) {
            return Response.fail("400", e.getMessage());
        }
    }

    private ShopFavoriteCommand toCommand(Long shopId) {
        ShopFavoriteCommand command = new ShopFavoriteCommand();
        command.setUserId(UserHolder.getUserId());
        command.setShopId(shopId);
        return command;
    }

    private ShopFavoriteResponseDTO toResponse(ShopFavoriteEntity entity) {
        ShopFavoriteResponseDTO response = new ShopFavoriteResponseDTO();
        response.setFavoriteId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setShopId(entity.getShopId());
        response.setFavorite(entity.getFavoriteStatus() != null && entity.getFavoriteStatus() == 1);
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private FavoriteShopListResponseDTO toListResponse(FavoriteShopListResult result) {
        FavoriteShopListResponseDTO response = new FavoriteShopListResponseDTO();
        response.setShops(result.getShops().stream().map(this::toFavoriteShopResponse).collect(Collectors.toList()));
        response.setHasMore(result.getHasMore());
        response.setLastId(result.getLastId());
        return response;
    }

    private FavoriteShopResponseDTO toFavoriteShopResponse(FavoriteShopEntity entity) {
        FavoriteShopResponseDTO response = new FavoriteShopResponseDTO();
        response.setFavoriteId(entity.getFavoriteId());
        response.setShopId(entity.getShopId());
        response.setShopName(entity.getShopName());
        response.setCategoryId(entity.getCategoryId());
        response.setImages(entity.getImages());
        response.setArea(entity.getArea());
        response.setAddress(entity.getAddress());
        response.setAvgPrice(entity.getAvgPrice());
        response.setSold(entity.getSold());
        response.setComments(entity.getComments());
        response.setScore(entity.getScore());
        response.setOpenHours(entity.getOpenHours());
        response.setFavoriteTime(entity.getFavoriteTime());
        return response;
    }
}
