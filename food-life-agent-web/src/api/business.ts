import { request } from './http'
import type { MealPackage, ShopCategory, ShopHomepage, ShopInfo } from '../types/shop'

export function queryCategories() {
  return request<ShopCategory[]>({
    url: '/business-api/shop-category/list',
    method: 'GET',
  })
}

export function queryShopsByCategory(categoryId: number, current = 1) {
  return request<ShopInfo[]>({
    url: '/business-api/shop/of/category',
    method: 'GET',
    params: { categoryId, current },
  })
}

export function queryShopByName(name: string, current = 1) {
  return request<ShopInfo[]>({
    url: '/business-api/shop/of/name',
    method: 'GET',
    params: { name, current },
  })
}

export function queryShopHomepage(shopId: number) {
  return request<ShopHomepage>({
    url: `/business-api/shop-homepage/${shopId}`,
    method: 'GET',
  })
}

export function queryPackagesByShop(shopId: number) {
  return request<MealPackage[]>({
    url: '/business-api/package/of/shop',
    method: 'GET',
    params: { shopId },
  })
}

export function favoriteShop(shopId: number) {
  return request({
    url: `/business-api/favorites/shops/${shopId}`,
    method: 'POST',
  })
}

export function unfavoriteShop(shopId: number) {
  return request({
    url: `/business-api/favorites/shops/${shopId}`,
    method: 'DELETE',
  })
}
