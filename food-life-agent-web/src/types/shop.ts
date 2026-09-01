export interface ShopCategory {
  id: number
  name: string
  icon?: string
  sort?: number
}

export interface ShopInfo {
  id: number
  name: string
  categoryId: number
  images: string
  area: string
  address: string
  longitude: number
  latitude: number
  avgPrice: number
  sold: number
  comments: number
  score: number
  openHours: string
}

export interface MealPackage {
  id: number
  shopId: number
  name: string
  description: string
  coverImage: string
  price: number
  originalPrice: number
  stock: number
  sold: number
  status: number
  useRule: string
}

export interface ShopReview {
  reviewId: number
  reviewNo: string
  userId: number
  shopId: number
  packageId: number
  orderId: number
  orderNo: string
  score: number
  content: string
  images: string
  createTime: string
}

export interface ShopReviewSummary {
  shopId: number
  comments: number
  score: number
  latestReviews: ShopReview[]
}

export interface ShopHomepage {
  userId: number
  shop: ShopInfo
  packages: MealPackage[]
  favorite: boolean
  reviewSummary: ShopReviewSummary
}
