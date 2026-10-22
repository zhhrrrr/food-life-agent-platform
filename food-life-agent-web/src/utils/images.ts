const FOOD_IMAGES = [
  '/images/meal-1.jpg',
  '/images/meal-2.jpg',
  '/images/meal-3.jpg',
  '/images/meal-4.jpg',
]

export function foodImage(seed?: number, source?: string) {
  if (source && source.trim().length > 0) {
    return source
  }
  const index = Math.abs(seed ?? 0) % FOOD_IMAGES.length
  return FOOD_IMAGES[index]
}

export function handleFoodImageError(event: Event, seed?: number) {
  const image = event.target as HTMLImageElement | null
  const fallback = foodImage(seed)
  if (image && image.getAttribute('src') !== fallback) image.src = fallback
}
